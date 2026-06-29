package lt.satsyuk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.ClientResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.model.Request;
import lt.satsyuk.model.RequestType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestProcessingService {

    private final RequestStateService requestStateService;
    private final ClientService clientService;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public void processClientCreateRequest(UUID requestId, String authClientId) {
        Request request = requestStateService.getRequired(requestId, authClientId);
        requestStateService.markProcessing(requestId, authClientId);

        try {
            if (request.getType() != RequestType.CLIENT_CREATE) {
                throw new IllegalStateException("Unsupported request type: " + request.getType());
            }

            CreateClientRequest createClientRequest = objectMapper.readValue(request.getRequestData(), CreateClientRequest.class);
            ClientResponse clientResponse = clientService.create(createClientRequest, authClientId);
            requestStateService.markCompleted(requestId, authClientId, writeJson(AppResponse.ok(clientResponse)));
        } catch (Exception ex) {
            log.error("Request {} processing failed", requestId, ex);
            requestStateService.markFailed(requestId, authClientId, writeJson(
                    AppResponse.error(AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                            messageService.getMessage("api.error.internalServerError"))
            ));
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize request processing payload", ex);
        }
    }
}
