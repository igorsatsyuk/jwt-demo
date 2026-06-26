package lt.satsyuk.exception;

import lombok.Getter;

@Getter
public class IdempotencyKeyConflictException extends RuntimeException {
    private final String idempotencyKey;

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Request with idempotency key=" + idempotencyKey + " already exists with different payload");
        this.idempotencyKey = idempotencyKey;
    }

    public String getMessageCode() {
        return "error.request.idempotencyKeyConflict";
    }
}
