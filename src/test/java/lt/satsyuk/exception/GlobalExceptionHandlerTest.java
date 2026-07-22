package lt.satsyuk.exception;

import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MessageSource messageSource;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void handleIdempotencyKeyConflictReturns409() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        IdempotencyKeyConflictException ex = new IdempotencyKeyConflictException(key);
        when(messageService.getMessage("error.request.idempotencyKeyConflict", new Object[]{key}))
                .thenReturn("Request with idempotency key=" + key + " already exists with different payload");

        ResponseEntity<AppResponse<Void>> response = handler.handleIdempotencyKeyConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(AppResponse.ErrorCode.CONFLICT.getCode());
        assertThat(response.getBody().message()).contains(key);
    }

    @Test
    void handleHttpMessageNotReadableReturns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed JSON", null, null);
        when(messageService.getMessage("error.request.invalidPayload"))
                .thenReturn("Invalid request payload");

        ResponseEntity<AppResponse<Void>> response = handler.handleHttpMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(AppResponse.ErrorCode.BAD_REQUEST.getCode());
        assertThat(response.getBody().message()).isEqualTo("Invalid request payload");
    }

    @Test
    void handleAccountUpdateInProgressReturns409() {
        UUID requestId = UUID.randomUUID();
        AccountUpdateInProgressException ex = new AccountUpdateInProgressException(requestId);
        when(messageService.getMessage("error.account.updateInProgress", new Object[]{requestId.toString()}))
                .thenReturn("Запрос на обновление баланса ещё выполняется: " + requestId);

        ResponseEntity<AppResponse<Void>> response = handler.handleAccountUpdateInProgress(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(AppResponse.ErrorCode.CONFLICT.getCode());
    }
}
