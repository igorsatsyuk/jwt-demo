package lt.satsyuk.exception;

import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;
    private final MessageService messageService;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AppResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElseGet(() -> messageService.getMessage("error.validation.failed"));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.BAD_REQUEST.getCode(), errorMessage));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AppResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.FORBIDDEN.getCode(),
                        messageService.getMessage("api.error.forbidden")));
    }

    @ExceptionHandler(ResourceAccessDeniedException.class)
    public ResponseEntity<AppResponse<Void>> handleResourceAccessDenied(ResourceAccessDeniedException ex) {
        String message = messageService.getMessage(ex.getMessageCode());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.FORBIDDEN.getCode(), message));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<AppResponse<Void>> handleAccountNotFound(AccountNotFoundException ex) {
        String message = messageService.getMessage(ex.getMessageCode(), new Object[]{String.valueOf(ex.getClientId())});
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.NOT_FOUND.getCode(), message));
    }

    @ExceptionHandler(AccountOptimisticLockException.class)
    public ResponseEntity<AppResponse<Void>> handleAccountOptimisticLock(AccountOptimisticLockException ex) {
        String message = messageService.getMessage(ex.getMessageCode(), new Object[]{String.valueOf(ex.getClientId())});
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.CONFLICT.getCode(), message));
    }

    @ExceptionHandler(ClientNotFoundException.class)
    public ResponseEntity<AppResponse<Void>> handleNotFound(ClientNotFoundException ex) {
        String message = messageService.getMessage(ex.getMessageCode(), new Object[]{String.valueOf(ex.getClientId())});
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.NOT_FOUND.getCode(), message));
    }

    @ExceptionHandler(ClientSearchQueryTooShortException.class)
    public ResponseEntity<AppResponse<Void>> handleClientSearchQueryTooShort(ClientSearchQueryTooShortException ex) {
        String message = messageService.getMessage(ex.getMessageCode(), new Object[]{String.valueOf(ex.getMinLength())});
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(RequestNotFoundException.class)
    public ResponseEntity<AppResponse<Void>> handleRequestNotFound(RequestNotFoundException ex) {
        String message = messageService.getMessage(ex.getMessageCode(), new Object[]{String.valueOf(ex.getRequestId())});
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.NOT_FOUND.getCode(), message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AppResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = messageService.getMessage("error.typeMismatch", new Object[]{String.valueOf(ex.getValue())});
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(PhoneAlreadyExistsException.class)
    public ResponseEntity<AppResponse<Void>> handlePhoneExists(PhoneAlreadyExistsException ex) {
        String message = messageService.getMessage(ex.getMessageCode(), new Object[]{String.valueOf(ex.getPhone())});
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.CONFLICT.getCode(), message));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<AppResponse<Void>> handleIdempotencyKeyConflict(IdempotencyKeyConflictException ex) {
        String message = messageService.getMessage(ex.getMessageCode(), new Object[]{ex.getIdempotencyKey()});
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.CONFLICT.getCode(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AppResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = messageService.getMessage("error.request.invalidPayload");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AppResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception caught by GlobalExceptionHandler", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AppResponse.<Void>error(AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        messageService.getMessage("api.error.internalServerError")));
    }
}
