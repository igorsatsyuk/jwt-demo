package lt.satsyuk.service;

import lt.satsyuk.exception.RequestNotFoundException;
import lt.satsyuk.model.Request;
import lt.satsyuk.model.RequestId;
import lt.satsyuk.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RequestStateService {

    private final RequestRepository requestRepository;

    @Transactional(readOnly = true)
    public Request getRequired(UUID requestId, String authClientId) {
        return requestRepository.findById(new RequestId(requestId, authClientId))
                .orElseThrow(() -> new RequestNotFoundException(requestId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID requestId, String authClientId) {
        Request request = requestRepository.findById(new RequestId(requestId, authClientId))
                .orElseThrow(() -> new RequestNotFoundException(requestId));
        request.markProcessing(now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID requestId, String authClientId, String responseData) {
        Request request = requestRepository.findById(new RequestId(requestId, authClientId))
                .orElseThrow(() -> new RequestNotFoundException(requestId));
        request.markCompleted(responseData, now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID requestId, String authClientId, String responseData) {
        Request request = requestRepository.findById(new RequestId(requestId, authClientId))
                .orElseThrow(() -> new RequestNotFoundException(requestId));
        request.markFailed(responseData, now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
