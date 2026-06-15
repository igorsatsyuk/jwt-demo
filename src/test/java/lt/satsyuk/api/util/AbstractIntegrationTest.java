package lt.satsyuk.api.util;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.config.KeycloakProperties;
import lt.satsyuk.dto.KeycloakTokenResponse;
import lt.satsyuk.dto.LoginRequest;
import lt.satsyuk.dto.LogoutRequest;
import lt.satsyuk.dto.RefreshRequest;
import lt.satsyuk.security.RateLimitingFilter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(AbstractIntegrationTest.FixedClockConfig.class)
public abstract class AbstractIntegrationTest {

    // Testcontainers-managed shared containers for integration tests
    private static final String DB_NAME = "appdb";
    private static final String DB_USER = "app";
    private static final String DB_PASS = "app";
    public static final String RESPONSE_MESSAGE_SHOULD_MATCH_EXPECTED = "Response message should match expected";
    public static final String RESPONSE_BODY_SHOULD_NOT_BE_NULL = "Response body should not be null";
    public static final String RESPONSE_DATA_SHOULD_NOT_BE_NULL = "Response data should not be null";
    public static final String RESPONSE_HTTP_STATUS_SHOULD_BE_OK = "Response HTTP status should be OK";
    public static final String RESPONSE_CODE_SHOULD_BE_ZERO = "Response code should be zero";
    public static final String RESPONSE_HTTP_STATUS_SHOULD_MATCH_EXPECTED = "Response HTTP status should match expected";
    public static final String RESPONSE_CODE_SHOULD_MATCH_EXPECTED = "Response code should match expected";
    private static final String RESPONSE_MESSAGE_SHOULD_NOT_BE_NULL = "Response message should not be null";
    public static final String FILTER_CONFIG_CACHE = "filterConfigCache";

    @Container
    protected static PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USER)
            .withPassword(DB_PASS)
            .withReuse(true);

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        if (!pg.isRunning()) {
            pg.start();
        }
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("spring.flyway.url", pg::getJdbcUrl);
        registry.add("spring.flyway.user", pg::getUsername);
        registry.add("spring.flyway.password", pg::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @BeforeAll
    static void checkDocker() {
        assumeTrue(pg != null && pg.isRunning(), "Postgres container must be running");
    }

    protected final KeycloakProperties props;

    protected RestTemplate restTemplate;

    protected final CacheManager cacheManager;

    protected final RateLimitingFilter rateLimitingFilter;

    protected AbstractIntegrationTest(KeycloakProperties props,
                                      CacheManager cacheManager,
                                      RateLimitingFilter rateLimitingFilter) {
        this.props = props;
        this.cacheManager = cacheManager;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    // Use a local ObjectMapper in Boot 4 PoC so integration tests do not depend on a Spring-managed bean.
    protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    protected static final String USERNAME = "user";
    protected static final String USER_PASSWORD = "password";
    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PASSWORD = "admin";
    protected static final String INVALID_GRANT = "invalid_grant";
    protected static final String INVALID_CLIENT = "invalid_client";

    @LocalServerPort
    protected int port;

    protected String mainUrl;

    protected String loginUrl;
    protected String refreshUrl;
    protected String logoutUrl;

    protected String clientUrl;
    protected String accountUrl;
    protected String requestUrl;

    @BeforeEach
    void initializeUrls() {
        ensureSchemaMigrated();

        mainUrl = "http://localhost:" + port + "/api";
        loginUrl = mainUrl + "/auth/login";
        refreshUrl = mainUrl + "/auth/refresh";
        logoutUrl = mainUrl + "/auth/logout";

        clientUrl = mainUrl + "/clients";
        accountUrl = mainUrl + "/accounts";
        requestUrl = mainUrl + "/requests";
        restTemplate = createTestRestTemplate();

        if (rateLimitingFilter != null) {
            rateLimitingFilter.clearBuckets();
        }

        if (cacheManager != null) {
            for (String cacheName : cacheManager.getCacheNames()) {
                if (FILTER_CONFIG_CACHE.equals(cacheName)) {
                    continue;
                }

                clearCache(cacheName);
            }
        }
    }

    protected void clearCache(String cacheName) {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private void ensureSchemaMigrated() {
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private RestTemplate createTestRestTemplate() {
        RestTemplate template = new RestTemplate(
                new HttpComponentsClientHttpRequestFactory(
                        HttpClients.custom()
                                .disableAutomaticRetries()
                                .build()
                )
        );
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            protected boolean hasError(HttpStatusCode statusCode) {
                return false;
            }
        });
        return template;
    }

    protected <T> void assertErrorBody(ResponseEntity<AppResponse<T>> response, int expectedCode, Object expectedMessage) {
        AppResponse<T> body = response.getBody();
        assertThat(body).as(RESPONSE_BODY_SHOULD_NOT_BE_NULL).isNotNull();
        assertThat(body.code()).as(RESPONSE_CODE_SHOULD_MATCH_EXPECTED).isEqualTo(expectedCode);
        if (expectedMessage instanceof String) {
            assertThat(body.message()).as(RESPONSE_MESSAGE_SHOULD_MATCH_EXPECTED).isEqualTo(expectedMessage);
        } else if (expectedMessage instanceof Set<?>) {
            String msg = body.message();
            assertThat(msg).as(RESPONSE_MESSAGE_SHOULD_NOT_BE_NULL).isNotNull();
            Set<String> actual = Arrays.stream(msg.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

            assertThat(actual).as(RESPONSE_MESSAGE_SHOULD_MATCH_EXPECTED).isEqualTo(expectedMessage);
        }
    }

    protected <T> void assertErrorStatusAndBody(ResponseEntity<AppResponse<T>> response,
                                            HttpStatus expectedStatus,
                                            int expectedCode,
                                            Object expectedMessage) {
        assertThat(response.getStatusCode()).as(RESPONSE_HTTP_STATUS_SHOULD_MATCH_EXPECTED).isEqualTo(expectedStatus);
        assertErrorBody(response, expectedCode, expectedMessage);
    }

    // Make this method fully generic and return T to avoid unchecked casts in tests
    protected <T> T assertStatusAndBodyAndReturnBody(ResponseEntity<AppResponse<T>> response,
                                                    HttpStatus expectedStatus,
                                                    Class<T> clazz) {
        assertThat(response.getStatusCode()).as(RESPONSE_HTTP_STATUS_SHOULD_BE_OK).isEqualTo(expectedStatus);
        assertThat(response.getBody()).as(RESPONSE_BODY_SHOULD_NOT_BE_NULL).isNotNull();

        AppResponse<T> api = response.getBody();
        assertThat(api).as(RESPONSE_BODY_SHOULD_NOT_BE_NULL).isNotNull();
        assertThat(api.code()).as(RESPONSE_CODE_SHOULD_BE_ZERO).isZero();

        Object raw = api.data();
        assertThat(raw).as(RESPONSE_DATA_SHOULD_NOT_BE_NULL).isNotNull();

        T data = objectMapper.convertValue(raw, clazz);
        assertThat(data).as(RESPONSE_DATA_SHOULD_NOT_BE_NULL).isNotNull();

        return data;
    }

    protected <T> T assertStatusAndBodyAndReturnBody(ResponseEntity<AppResponse<T>> response, Class<T> clazz) {
        return assertStatusAndBodyAndReturnBody(response, HttpStatus.OK, clazz);
    }

    private HttpHeaders createHeaders(String token, String acceptLanguage, String dpopProof, boolean dpopScheme) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON, MediaType.ALL));
        if (token != null && !token.isEmpty()) {
            if (dpopScheme) {
                headers.set(HttpHeaders.AUTHORIZATION, "DPoP " + token);
            } else {
                headers.setBearerAuth(token);
            }
        }
        if (dpopProof != null && !dpopProof.isEmpty()) {
            headers.set("DPoP", dpopProof);
        }
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            headers.set("Accept-Language", acceptLanguage);
        }

        return headers;
    }

    protected <T> ResponseEntity<AppResponse<T>> requestPost(String url, String token, String acceptLanguage, Object body, ParameterizedTypeReference<AppResponse<T>> responseType) {
        HttpHeaders headers = createHeaders(token, acceptLanguage, null, false);

        return exchangeForAppResponse(url, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    protected <T> ResponseEntity<AppResponse<T>> requestPostDpop(String url,
                                                                 String token,
                                                                 String acceptLanguage,
                                                                 String dpopProof,
                                                                 Object body,
                                                                 ParameterizedTypeReference<AppResponse<T>> responseType) {
        HttpHeaders headers = createHeaders(token, acceptLanguage, dpopProof, true);

        return exchangeForAppResponse(url, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    protected ResponseEntity<AppResponse<Object>> requestPost(String url, String token, String acceptLanguage, Record body) {
        return requestPost(url, token, acceptLanguage, body, new ParameterizedTypeReference<>() {});
    }

    protected ResponseEntity<AppResponse<Object>> requestPost(String url, String acceptLanguage, Record body) {
        return requestPost(url, null, acceptLanguage, body);
    }

    protected <T> ResponseEntity<AppResponse<T>> requestGet(String url, String token, ParameterizedTypeReference<AppResponse<T>> responseType) {
        HttpHeaders headers = createHeaders(token, null, null, false);

        return exchangeForAppResponse(url, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    protected <T> ResponseEntity<AppResponse<T>> requestGetDpop(String url, String token, String dpopProof, ParameterizedTypeReference<AppResponse<T>> responseType) {
        HttpHeaders headers = createHeaders(token, null, dpopProof, true);

        return exchangeForAppResponse(url, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    protected ResponseEntity<AppResponse<Object>> requestGet(String url, String token) {
        return requestGet(url, token, new ParameterizedTypeReference<>() {});
    }

    protected ResponseEntity<AppResponse<Object>> requestGet(String url) {
        return requestGet(url, null);
    }

    protected ResponseEntity<AppResponse<KeycloakTokenResponse>> loginRequest(String username,
                                                               String password,
                                                               String clientId,
                                                               String clientSecret) {
        return loginRequest(username, password, clientId, clientSecret, null);
    }

    protected ResponseEntity<AppResponse<KeycloakTokenResponse>> loginRequest(String username,
                                                                               String password,
                                                                               String clientId,
                                                                               String clientSecret,
                                                                               String dpopProof) {

        LoginRequest request = new LoginRequest(
                username,
                password,
                clientId,
                clientSecret
        );
        return requestPostDpop(loginUrl, null, null, dpopProof, request, new ParameterizedTypeReference<>() {});
    }

    protected ResponseEntity<AppResponse<KeycloakTokenResponse>> loginRequest(String username,
                                                               String password) {

        return loginRequest(username, password, props.getClientId(), props.getClientSecret());
    }

    protected KeycloakTokenResponse loginAndGetData(String username, String password) {
        ResponseEntity<AppResponse<KeycloakTokenResponse>> response = loginRequest(username, password);

        return assertStatusAndBodyAndReturnBody(response, KeycloakTokenResponse.class);
    }

    protected String loginAndGetAccess(String username, String password) {
        return loginAndGetData(username, password).getAccessToken();
    }

    protected String loginAndGetRefresh(String username, String password) {
        return loginAndGetData(username, password).getRefreshToken();
    }

    protected ResponseEntity<AppResponse<Void>> logoutRequest(String refreshToken,
                                                                String clientId,
                                                                String clientSecret) {
        return logoutRequest(refreshToken, clientId, clientSecret, null);
    }

    protected ResponseEntity<AppResponse<Void>> logoutRequest(String refreshToken,
                                                              String clientId,
                                                              String clientSecret,
                                                              String dpopProof) {
        LogoutRequest logoutRequest = new LogoutRequest(refreshToken, clientId, clientSecret);
        return requestPostDpop(logoutUrl, null, null, dpopProof, logoutRequest, new ParameterizedTypeReference<>() {});
    }

    protected ResponseEntity<AppResponse<Void>> logoutRequest(String refreshToken) {
        return logoutRequest(refreshToken, props.getClientId(), props.getClientSecret());
    }

    protected ResponseEntity<AppResponse<KeycloakTokenResponse>> refreshRequest(String refreshToken,
                                                                String clientId,
                                                                String clientSecret) {
        return refreshRequest(refreshToken, clientId, clientSecret, null);
    }

    protected ResponseEntity<AppResponse<KeycloakTokenResponse>> refreshRequest(String refreshToken,
                                                                                String clientId,
                                                                                String clientSecret,
                                                                                String dpopProof) {
        RefreshRequest request = new RefreshRequest(refreshToken, clientId, clientSecret);
        return requestPostDpop(refreshUrl, null, null, dpopProof, request, new ParameterizedTypeReference<>() {});
    }

    protected ResponseEntity<AppResponse<KeycloakTokenResponse>> refreshRequest(String refreshToken) {
        return refreshRequest(refreshToken, props.getClientId(), props.getClientSecret());
    }

    // Helper: do POST and return typed data (convert via ObjectMapper)
    protected <T> T postAndReturnData(String url, String token, Object body, Class<T> clazz) {
        ResponseEntity<AppResponse<T>> resp = requestPost(url, token, null, body, new ParameterizedTypeReference<>() {});
        return assertStatusAndBodyAndReturnBody(resp, clazz);
    }

    protected <T> T postAndReturnData(String url, String token, Object body, HttpStatus expectedStatus, Class<T> clazz) {
        ResponseEntity<AppResponse<T>> resp = requestPost(url, token, null, body, new ParameterizedTypeReference<>() {});
        return assertStatusAndBodyAndReturnBody(resp, expectedStatus, clazz);
    }

    // Helper: do GET and return typed data (convert via ObjectMapper)
    protected <T> T getAndReturnData(String url, String token, Class<T> clazz) {
        ResponseEntity<AppResponse<T>> resp = requestGet(url, token, new ParameterizedTypeReference<>() {});
        return assertStatusAndBodyAndReturnBody(resp, clazz);
    }

    private <T> ResponseEntity<AppResponse<T>> exchangeForAppResponse(String url,
                                                                      HttpMethod method,
                                                                      HttpEntity<?> entity,
                                                                       ParameterizedTypeReference<AppResponse<T>> responseType) {
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
        AppResponse<T> body = deserializeAppResponse(response.getBody(), responseType);

        if (body == null) {
            body = fallbackErrorBody(response.getStatusCode());
        }

        return new ResponseEntity<>(body, response.getHeaders(), response.getStatusCode());
    }

    private <T> AppResponse<T> deserializeAppResponse(String rawBody,
                                                      ParameterizedTypeReference<AppResponse<T>> responseType) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }

        try {
            if (responseType != null) {
                return objectMapper.readValue(
                        rawBody,
                        objectMapper.getTypeFactory().constructType(responseType.getType())
                );
            }

            return objectMapper.readValue(rawBody, new TypeReference<AppResponse<T>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize API response: " + rawBody, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> AppResponse<T> fallbackErrorBody(HttpStatusCode statusCode) {
        if (statusCode != null && statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
            return (AppResponse<T>) AppResponse.error(
                    AppResponse.ErrorCode.UNAUTHORIZED.getCode(),
                    AppResponse.ErrorCode.UNAUTHORIZED.getDescription()
            );
        }

        if (statusCode != null && statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            return (AppResponse<T>) AppResponse.error(
                    AppResponse.ErrorCode.FORBIDDEN.getCode(),
                    AppResponse.ErrorCode.FORBIDDEN.getDescription()
            );
        }

        return null;
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TestTime.FIXED_INSTANT, java.time.ZoneOffset.UTC);
        }
    }
}
