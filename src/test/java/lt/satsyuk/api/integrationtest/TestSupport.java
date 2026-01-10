package lt.satsyuk.api.integrationtest;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

public class TestSupport {

    protected final WebTestClient client;
    protected final int port;

    public TestSupport(WebTestClient client, int port) {
        this.client = client;
        this.port = port;
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected Map<String, Object> post(String path, Object body) {
        return client.post()
                .uri(url(path))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();
    }

    protected Map<String, Object> get(String path, String token) {
        return client.get()
                .uri(url(path))
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();
    }

    @SuppressWarnings("unchecked")
    protected String accessToken(Map<String, Object> body) {
        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return null;
        }
        return (String) data.get("access_token");
    }

    @SuppressWarnings("unchecked")
    protected String refreshToken(Map<String, Object> body) {
        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return null;
        }
        return (String) data.get("refresh_token");
    }

    protected void assertError(Map<String, Object> body, int code, String message) {
        assert body.get("code").equals(code);
        assert body.get("message").equals(message);
    }
}