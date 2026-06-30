package lt.satsyuk.exception;

import lombok.Getter;

@Getter
public class AccountUpdateFailedException extends RuntimeException {
    private final String errorMessage;

    public AccountUpdateFailedException(String errorMessage) {
        super("Account update request failed: " + errorMessage);
        this.errorMessage = errorMessage;
    }

    public String getMessageCode() {
        return "error.account.updateFailed";
    }
}
