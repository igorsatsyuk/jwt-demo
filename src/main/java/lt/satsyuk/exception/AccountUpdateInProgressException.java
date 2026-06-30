package lt.satsyuk.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class AccountUpdateInProgressException extends RuntimeException {
    private final UUID requestId;

    public AccountUpdateInProgressException(UUID requestId) {
        super("Account update request is still in progress: " + requestId);
        this.requestId = requestId;
    }

    public String getMessageCode() {
        return "error.account.updateInProgress";
    }
}
