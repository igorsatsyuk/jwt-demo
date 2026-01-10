package lt.satsyuk.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class KeycloakAuthException extends RuntimeException {

    private final HttpStatus status;
    private final String keycloakMessage;

    public KeycloakAuthException(String message, HttpStatus status, String keycloakMessage) {
        super(message);
        this.status = status;
        this.keycloakMessage = keycloakMessage;
    }
}