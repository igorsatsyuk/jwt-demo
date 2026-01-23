package lt.satsyuk.api.integrationtest;

import lt.satsyuk.MainApplication;
import lt.satsyuk.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MainApplication.class)
class AuthValidationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void login_emptyFields_badRequest() {
        LoginRequest request = new LoginRequest("", "", "", "");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(40001);
    }

    @Test
    void refresh_emptyBody_badRequest() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/refresh", Map.of(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testDocker() {
        System.setProperty("docker.host", "unix:///var/run/docker.sock");
        try (GenericContainer<?> container = new GenericContainer<>("alpine:3.18")
                .withCommand("echo", "hello from testcontainers")) {
            container.start();
            System.out.println(container.getLogs());
        }
    }

    @Test
    void debugDocker() {
        File f = new File("/var/run/docker.sock");
        System.out.println("SOCKET_EXISTS=" + f.exists());
        System.out.println("CAN_READ=" + f.canRead());
        System.out.println("CAN_WRITE=" + f.canWrite());
        System.out.println("PWD=" + new File(".").getAbsolutePath());
        System.out.println("USER=" + System.getProperty("user.name"));
        System.out.println("HOME=" + System.getProperty("user.home"));
        System.out.println("OS=" + System.getProperty("os.name"));
    }
}
