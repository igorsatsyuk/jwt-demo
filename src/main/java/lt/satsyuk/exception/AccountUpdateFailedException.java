package lt.satsyuk.exception;

import lombok.Getter;

@Getter
public class AccountUpdateFailedException extends RuntimeException {
    private final int errorCode;
    private final String errorMessage;

    public AccountUpdateFailedException(int errorCode, String errorMessage) {
        super("Account update request failed: " + errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getMessageCode() {
        return "error.account.updateFailed";
    }
}
