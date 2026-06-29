package lt.satsyuk.exception;

import lombok.Getter;

@Getter
public class ResourceAccessDeniedException extends RuntimeException {

    public ResourceAccessDeniedException() {
        super("Access denied");
    }

    public String getMessageCode() {
        return "error.access.denied";
    }
}
