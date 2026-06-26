package lt.satsyuk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.dto.RequestAcceptedResponse;
import lt.satsyuk.dto.RequestStatusResponse;
import lt.satsyuk.api.util.TestTime;
import lt.satsyuk.exception.IdempotencyKeyConflictException;
import lt.satsyuk.model.Request;
import lt.satsyuk.model.RequestStatus;
import lt.satsyuk.model.RequestType;
import lt.satsyuk.repository.RequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.SchedulerException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private RequestSchedulerService requestSchedulerService;

    @Mock
    private RequestStateService requestStateService;

    @Mock
    private MessageService messageService;

    private RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestRepository,
                requestSchedulerService,
                requestStateService,
                objectMapper,
                messageService
        );
    }

    @Test
    void submitClientCreateRequestStoresSerializedPayloadAndSchedulesJob() throws Exception {
        CreateClientRequest createClientRequest = new CreateClientRequest(null, "John", "Doe", "+37061234567");
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestAcceptedResponse response = requestService.submitClientCreateRequest(createClientRequest);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(requestCaptor.capture());
        Request savedRequest = requestCaptor.getValue();

        assertThat(response.requestId()).isEqualTo(savedRequest.getId());
        assertThat(response.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(savedRequest.getType()).isEqualTo(RequestType.CLIENT_CREATE);
        assertThat(savedRequest.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(savedRequest.getRequestData()).isEqualTo(objectMapper.writeValueAsString(createClientRequest));
        verify(requestSchedulerService).scheduleClientCreateRequest(savedRequest.getId());
    }

    @Test
    void submitClientCreateRequestUsesIdempotencyKeyAsRequestId() {
        UUID idempotencyKey = UUID.randomUUID();
        CreateClientRequest createClientRequest = new CreateClientRequest(idempotencyKey, "John", "Doe", "+37061234567");
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestAcceptedResponse response = requestService.submitClientCreateRequest(createClientRequest);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(requestCaptor.capture());
        Request savedRequest = requestCaptor.getValue();

        assertThat(savedRequest.getId()).isEqualTo(idempotencyKey);
        assertThat(response.requestId()).isEqualTo(idempotencyKey);
    }

    @Test
    void submitClientCreateRequestReturnsExistingRequestForSameIdempotencyKeyAndPayload() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CreateClientRequest createClientRequest = new CreateClientRequest(idempotencyKey, "John", "Doe", "+37061234567");
        OffsetDateTime now = OffsetDateTime.now();
        Request existingRequest = Request.builder()
                .id(idempotencyKey)
                .type(RequestType.CLIENT_CREATE)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData(objectMapper.writeValueAsString(createClientRequest))
                .build();
        when(requestRepository.findById(idempotencyKey)).thenReturn(Optional.of(existingRequest));

        RequestAcceptedResponse response = requestService.submitClientCreateRequest(createClientRequest);

        assertThat(response.requestId()).isEqualTo(idempotencyKey);
        assertThat(response.status()).isEqualTo(RequestStatus.COMPLETED);
        verify(requestRepository, never()).save(any(Request.class));
        verify(requestSchedulerService, never()).scheduleClientCreateRequest(any(UUID.class));
    }

    @Test
    void submitClientCreateRequestThrowsConflictForSameIdempotencyKeyDifferentPayload() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CreateClientRequest incoming = new CreateClientRequest(idempotencyKey, "John", "Doe", "+37061234567");
        CreateClientRequest existingPayload = new CreateClientRequest(idempotencyKey, "Jane", "Roe", "+37069999999");
        OffsetDateTime now = OffsetDateTime.now();
        Request existingRequest = Request.builder()
                .id(idempotencyKey)
                .type(RequestType.CLIENT_CREATE)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData(objectMapper.writeValueAsString(existingPayload))
                .build();
        when(requestRepository.findById(idempotencyKey)).thenReturn(Optional.of(existingRequest));

        assertThatThrownBy(() -> requestService.submitClientCreateRequest(incoming))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining(idempotencyKey.toString());
    }

    @Test
    void getRequestStatusReturnsNestedJsonResponse() {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime now = TestTime.FIXED_OFFSET_DATE_TIME;
        Request request = Request.builder()
                .id(requestId)
                .type(RequestType.CLIENT_CREATE)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData("{\"firstName\":\"John\"}")
                .responseData("{\"code\":0,\"data\":{\"id\":1,\"phone\":\"+37061234567\"},\"message\":\"OK\"}")
                .build();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        RequestStatusResponse response = requestService.getRequestStatus(requestId);

        assertThat(response.response()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> nestedResponse = (Map<String, Object>) response.response();

        assertThat(nestedResponse)
                .containsEntry("code", 0)
                .containsEntry("message", "OK");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) nestedResponse.get("data");

        assertThat(data)
                .containsEntry("id", 1)
                .containsEntry("phone", "+37061234567");
    }

    @Test
    void submitClientCreateRequestMarksProcessingErrorWhenSchedulingFails() throws Exception {
        CreateClientRequest createClientRequest = new CreateClientRequest(null, "John", "Doe", "+37061234567");
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageService.getMessage("error.request.schedulingFailed")).thenReturn("Failed to schedule request processing");
        doThrow(new SchedulerException("boom"))
                .when(requestSchedulerService)
                .scheduleClientCreateRequest(any(UUID.class));

        assertThatThrownBy(() -> requestService.submitClientCreateRequest(createClientRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to schedule request processing for requestId=");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(requestCaptor.capture());
        Request savedRequest = requestCaptor.getValue();
        verify(requestStateService).markFailed(
                savedRequest.getId(),
                objectMapper.writeValueAsString(AppResponse.error(
                        AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "Failed to schedule request processing"
                ))
        );
    }
}




