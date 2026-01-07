Spring Boot + Keycloak 26.x + Integration Tests

## 🚀 О проекте

Этот проект демонстрирует полноценную интеграцию **Spring Boot (Resource Server)** и **Keycloak 26.x**:

- 🔐 Авторизация через Keycloak (password grant)  
- 🔁 Refresh токены  
- 🚪 Logout (ревокация refresh токена)  
- 👤 Роли `USER` и `ADMIN` из `realm_access.roles`  
- 🛡 Защита REST‑эндпоинтов через `@PreAuthorize`  
- 🧪 Интеграционные тесты WebTestClient, использующие реальный Keycloak  

Проект полностью воспроизводим благодаря **docker-compose** и **realm-export.json**.

---

## 📂 Структура проекта

```
project/
│
├── docker-compose.yaml
├── keycloak/
│   └── realm-export.json
│
├── src/
│   ├── main/java/...
│   └── test/java/...
│
└── pom.xml
```

---

## 🐳 Запуск Keycloak через Docker

### 1. Установи Docker Desktop  
`https://www.docker.com/products/docker-desktop/` [(docker.com in Bing)](https://www.bing.com/search?q="https%3A%2F%2Fwww.docker.com%2Fproducts%2Fdocker-desktop%2F")

### 2. Запусти Keycloak

В корне проекта:

```bash
docker compose up -d
```

Keycloak поднимется на:

```
http://localhost:8080
```

### 3. Доступ в админ‑панель

```
http://localhost:8080/admin
```

Логин:

```
admin
admin
```

### 4. Что импортируется автоматически

Файл `keycloak/realm-export.json` создаёт:

#### Realm
```
my-realm
```

#### Клиент
```
spring-app
```

- Confidential  
- Direct Access Grants = ON  
- Full Scope Allowed = ON  

#### Пользователи

| Username | Password | Roles |
|----------|----------|--------|
| user     | password | USER   |
| admin    | password | ADMIN  |

---

## 🔧 Настройки Spring Boot

`application.properties`:

```properties
keycloak.realm=my-realm
keycloak.auth-server-url=http://localhost:8080
keycloak.token-uri=${keycloak.auth-server-url}/realms/${keycloak.realm}/protocol/openid-connect/token
keycloak.logout-uri=${keycloak.auth-server-url}/realms/${keycloak.realm}/protocol/openid-connect/logout
keycloak.client-id=spring-app
keycloak.client-secret=CHANGE_ME

spring.security.oauth2.resourceserver.jwt.issuer-uri=${keycloak.auth-server-url}/realms/${keycloak.realm}
```

---

## 🔐 SecurityConfig

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> {
                            jwt.jwtAuthenticationConverter(jwtAuthConverter());
                        })
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {

        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthorityPrefix("");
        converter.setAuthoritiesClaimName("realm_access.roles");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }
}
```

---

## 🔥 Защищённые эндпоинты

```java
@GetMapping("/api/user")
@PreAuthorize("hasAuthority('USER')")
public String user() {
    return "user endpoint";
}

@GetMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public String admin() {
    return "admin endpoint";
}
```

---

## 🧪 Интеграционные тесты

Запуск:

```bash
mvn test
```

Проверяется:

- ✔ login  
- ✔ доступ к защищённым эндпоинтам  
- ✔ refresh токена  
- ✔ logout (ревокация refresh токена)  
- ✔ запрет доступа user → /api/admin  

---

## 🧱 Пример теста

```java
@Test
void loginAndAccessUserEndpoint() {
    var token = web.post()
            .uri("/auth/login")
            .bodyValue(new AuthRequest("user", "password"))
            .exchange()
            .expectStatus().isOk()
            .expectBody(KeycloakTokenResponse.class)
            .returnResult()
            .getResponseBody();

    web.get()
            .uri("/api/user")
            .header("Authorization", "Bearer " + token.access_token())
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("user endpoint");
}
```

---

## 🧹 Остановка Keycloak

```bash
docker compose down
```

---

## 🎉 Готово!

