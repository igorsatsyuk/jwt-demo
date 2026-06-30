package lt.satsyuk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lt.satsyuk.dto.AppResponse;
import lt.satsyuk.dto.CreateClientRequest;
import lt.satsyuk.dto.RequestAcceptedResponse;
import lt.satsyuk.dto.RequestStatusResponse;
import lt.satsyuk.api.util.TestTime;
import lt.satsyuk.exception.IdempotencyKeyConflictException;
import lt.satsyuk.exception.RequestNotFoundException;
import lt.satsyuk.model.Request;
import lt.satsyuk.model.RequestId;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    private static final String CLIENT_ID = "spring-app";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private RequestSchedulerService requestSchedulerService;

    @Mock
    private RequestStateService requestStateService;

    @Mock
    private MessageService messageService;

    @Mock
    private SecurityService securityService;

    private RequestService requestService;

    @BeforeEach
    void setUp() {
        lenient().when(securityService.clientId()).thenReturn(CLIENT_ID);
        requestService = new RequestService(
                requestRepository,
                requestSchedulerService,
                requestStateService,
                objectMapper,
                messageService,
                securityService
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
        assertThat(savedRequest.getAuthClientId()).isEqualTo(CLIENT_ID);
        assertThat(savedRequest.getRequestData()).isEqualTo(objectMapper.writeValueAsString(createClientRequest));
        verify(requestSchedulerService).scheduleClientCreateRequest(savedRequest.getId(), CLIENT_ID);
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
        assertThat(savedRequest.getAuthClientId()).isEqualTo(CLIENT_ID);
        assertThat(response.requestId()).isEqualTo(idempotencyKey);
    }

    @Test
    void submitClientCreateRequestReturnsExistingRequestForSameIdempotencyKeyAndPayload() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CreateClientRequest createClientRequest = new CreateClientRequest(idempotencyKey, "John", "Doe", "+37061234567");
        OffsetDateTime now = OffsetDateTime.now();
        Request existingRequest = Request.builder()
                .requestId(new RequestId(idempotencyKey, CLIENT_ID))
                .type(RequestType.CLIENT_CREATE)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData(objectMapper.writeValueAsString(createClientRequest))
                .build();
        when(requestRepository.findById(new RequestId(idempotencyKey, CLIENT_ID)))
                .thenReturn(Optional.of(existingRequest));

        RequestAcceptedResponse response = requestService.submitClientCreateRequest(createClientRequest);

        assertThat(response.requestId()).isEqualTo(idempotencyKey);
        assertThat(response.status()).isEqualTo(RequestStatus.COMPLETED);
        verify(requestRepository, never()).save(any(Request.class));
        verify(requestSchedulerService, never()).scheduleClientCreateRequest(any(UUID.class), any(String.class));
    }

    @Test
    void submitClientCreateRequestAllowsSameIdempotencyKeyForDifferentClient() {
        UUID idempotencyKey = UUID.randomUUID();
        CreateClientRequest createClientRequest = new CreateClientRequest(idempotencyKey, "John", "Doe", "+37061234567");
        when(requestRepository.findById(new RequestId(idempotencyKey, CLIENT_ID)))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestAcceptedResponse response = requestService.submitClientCreateRequest(createClientRequest);

        assertThat(response.requestId()).isEqualTo(idempotencyKey);
        verify(requestRepository).save(any(Request.class));
    }

    @Test
    void submitClientCreateRequestThrowsConflictForSameIdempotencyKeySameClientDifferentPayload() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CreateClientRequest incoming = new CreateClientRequest(idempotencyKey, "John", "Doe", "+37061234567");
        CreateClientRequest existingPayload = new CreateClientRequest(idempotencyKey, "Jane", "Roe", "+37069999999");
        OffsetDateTime now = OffsetDateTime.now();
        Request existingRequest = Request.builder()
                .requestId(new RequestId(idempotencyKey, CLIENT_ID))
                .type(RequestType.CLIENT_CREATE)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData(objectMapper.writeValueAsString(existingPayload))
                .build();
        when(requestRepository.findById(new RequestId(idempotencyKey, CLIENT_ID)))
                .thenReturn(Optional.of(existingRequest));

        assertThatThrownBy(() -> requestService.submitClientCreateRequest(incoming))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining(idempotencyKey.toString());
    }

    @Test
    void getRequestStatusReturnsNestedJsonResponse() {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime now = TestTime.FIXED_OFFSET_DATE_TIME;
        Request request = Request.builder()
                .requestId(new RequestId(requestId, CLIENT_ID))
                .type(RequestType.CLIENT_CREATE)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData("{\"firstName\":\"John\"}")
                .responseData("{\"code\":0,\"data\":{\"id\":1,\"phone\":\"+37061234567\"},\"message\":\"OK\"}")
                .build();
        when(requestRepository.findByIdAndAuthClientId(requestId, CLIENT_ID)).thenReturn(Optional.of(request));

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
    void getRequestStatusReturnsLegacyRequestWithUnknownClientId() {
        UUID requestId = UUID.randomUUID();
        OffsetDateTime now = TestTime.FIXED_OFFSET_DATE_TIME;
        Request request = Request.builder()
                .requestId(new RequestId(requestId, "unknown"))
                .type(RequestType.CLIENT_CREATE)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData("{\"firstName\":\"John\"}")
                .responseData("{\"code\":0,\"data\":{\"id\":1},\"message\":\"OK\"}")
                .build();
        when(requestRepository.findByIdAndAuthClientId(requestId, CLIENT_ID)).thenReturn(Optional.empty());
        when(requestRepository.findByIdAndAuthClientId(requestId, "unknown")).thenReturn(Optional.of(request));

        RequestStatusResponse response = requestService.getRequestStatus(requestId);

        assertThat(response.requestId()).isEqualTo(requestId);
    }

    @Test
    void getRequestStatusThrowsNotFoundWhenAuthClientIdMismatch() {
        UUID requestId = UUID.randomUUID();
        when(requestRepository.findByIdAndAuthClientId(requestId, CLIENT_ID)).thenReturn(Optional.empty());
        when(requestRepository.findByIdAndAuthClientId(requestId, "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.getRequestStatus(requestId))
                .isInstanceOf(RequestNotFoundException.class);
    }

    @Test
    void submitClientCreateRequestMarksProcessingErrorWhenSchedulingFails() throws Exception {
        CreateClientRequest createClientRequest = new CreateClientRequest(null, "John", "Doe", "+37061234567");
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageService.getMessage("error.request.schedulingFailed")).thenReturn("Failed to schedule request processing");
        doThrow(new SchedulerException("boom"))
                .when(requestSchedulerService)
                .scheduleClientCreateRequest(any(UUID.class), any(String.class));

        assertThatThrownBy(() -> requestService.submitClientCreateRequest(createClientRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to schedule request processing for requestId=");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(requestCaptor.capture());
        Request savedRequest = requestCaptor.getValue();
        verify(requestStateService).markFailed(
                savedRequest.getId(),
                CLIENT_ID,
                objectMapper.writeValueAsString(AppResponse.error(
                        AppResponse.ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "Failed to schedule request processing"
                ))
        );
    }

    @Test
    void createPendingRequestIfAbsentCreatesNewRequestWithoutIdempotencyKey() {
        lt.satsyuk.dto.UpdateBalanceRequest payload = new lt.satsyuk.dto.UpdateBalanceRequest(null, 1L, new java.math.BigDecimal("50.00"));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                null, payload, RequestType.UPDATE_BALANCE_PESSIMISTIC, CLIENT_ID);

        assertThat(result.alreadyExisted()).isFalse();
        assertThat(result.requestId()).isNotNull();
        assertThat(result.savedResponseData()).isNull();

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(captor.capture());
        Request saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(RequestType.UPDATE_BALANCE_PESSIMISTIC);
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(saved.getAuthClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void createPendingRequestIfAbsentUsesIdempotencyKeyAsRequestId() {
        UUID idempotencyKey = UUID.randomUUID();
        lt.satsyuk.dto.UpdateBalanceRequest payload = new lt.satsyuk.dto.UpdateBalanceRequest(idempotencyKey, 1L, new java.math.BigDecimal("50.00"));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                idempotencyKey, payload, RequestType.UPDATE_BALANCE_OPTIMISTIC, CLIENT_ID);

        assertThat(result.requestId()).isEqualTo(idempotencyKey);
        assertThat(result.alreadyExisted()).isFalse();

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(idempotencyKey);
    }

    @Test
    void createPendingRequestIfAbsentReturnsExistingRequestForSamePayload() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        lt.satsyuk.dto.UpdateBalanceRequest payload = new lt.satsyuk.dto.UpdateBalanceRequest(idempotencyKey, 1L, new java.math.BigDecimal("50.00"));
        OffsetDateTime now = OffsetDateTime.now();
        Request existingRequest = Request.builder()
                .requestId(new RequestId(idempotencyKey, CLIENT_ID))
                .type(RequestType.UPDATE_BALANCE_PESSIMISTIC)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData(objectMapper.writeValueAsString(payload))
                .responseData("{\"code\":0,\"data\":{\"accountId\":1,\"clientId\":1,\"balance\":\"60.00\"},\"message\":\"OK\"}")
                .build();
        when(requestRepository.findById(new RequestId(idempotencyKey, CLIENT_ID)))
                .thenReturn(Optional.of(existingRequest));

        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                idempotencyKey, payload, RequestType.UPDATE_BALANCE_PESSIMISTIC, CLIENT_ID);

        assertThat(result.alreadyExisted()).isTrue();
        assertThat(result.requestId()).isEqualTo(idempotencyKey);
        assertThat(result.savedResponseData()).contains("\"code\":0");
        verify(requestRepository, never()).save(any(Request.class));
    }

    @Test
    void createPendingRequestIfAbsentThrowsConflictForSameKeyDifferentPayload() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        lt.satsyuk.dto.UpdateBalanceRequest incoming = new lt.satsyuk.dto.UpdateBalanceRequest(idempotencyKey, 1L, new java.math.BigDecimal("50.00"));
        lt.satsyuk.dto.UpdateBalanceRequest existingPayload = new lt.satsyuk.dto.UpdateBalanceRequest(idempotencyKey, 1L, new java.math.BigDecimal("99.00"));
        OffsetDateTime now = OffsetDateTime.now();
        Request existingRequest = Request.builder()
                .requestId(new RequestId(idempotencyKey, CLIENT_ID))
                .type(RequestType.UPDATE_BALANCE_PESSIMISTIC)
                .status(RequestStatus.COMPLETED)
                .createdAt(now)
                .statusChangedAt(now)
                .requestData(objectMapper.writeValueAsString(existingPayload))
                .build();
        when(requestRepository.findById(new RequestId(idempotencyKey, CLIENT_ID)))
                .thenReturn(Optional.of(existingRequest));

        assertThatThrownBy(() -> requestService.createPendingRequestIfAbsent(
                idempotencyKey, incoming, RequestType.UPDATE_BALANCE_PESSIMISTIC, CLIENT_ID))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining(idempotencyKey.toString());
    }

    @Test
    void createPendingRequestIfAbsentCreatesNewRequestWhenNoExistingFound() {
        UUID idempotencyKey = UUID.randomUUID();
        lt.satsyuk.dto.UpdateBalanceRequest payload = new lt.satsyuk.dto.UpdateBalanceRequest(idempotencyKey, 1L, new java.math.BigDecimal("50.00"));
        when(requestRepository.findById(new RequestId(idempotencyKey, CLIENT_ID)))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                idempotencyKey, payload, RequestType.UPDATE_BALANCE_PESSIMISTIC, CLIENT_ID);

        assertThat(result.requestId()).isEqualTo(idempotencyKey);
        assertThat(result.alreadyExisted()).isFalse();
        verify(requestRepository).save(any(Request.class));
    }

    @Test
    void jsonEqualsReturnsTrueForBothNull() {
        assertThat(requestService.jsonEquals(null, null)).isTrue();
    }

    @Test
    void jsonEqualsReturnsFalseWhenOneIsNull() {
        assertThat(requestService.jsonEquals(null, "{}")).isFalse();
        assertThat(requestService.jsonEquals("{}", null)).isFalse();
    }

    @Test
    void jsonEqualsReturnsTrueForSemanticallyEqualJson() {
        assertThat(requestService.jsonEquals("{\"a\":1,\"b\":2}", "{\"b\":2,\"a\":1}")).isTrue();
    }

    @Test
    void jsonEqualsReturnsFalseForDifferentJson() {
        assertThat(requestService.jsonEquals("{\"a\":1}", "{\"a\":2}")).isFalse();
    }

    @Test
    void jsonEqualsFallsBackToStringComparisonForMalformedJson() {
        assertThat(requestService.jsonEquals("{bad", "{bad")).isTrue();
        assertThat(requestService.jsonEquals("{bad", "{other")).isFalse();
    }

    @Test
    void createPendingRequestIfAbsentSkipsIdempotencyCheckWhenKeyIsNull() {
        lt.satsyuk.dto.UpdateBalanceRequest payload = new lt.satsyuk.dto.UpdateBalanceRequest(null, 1L, new java.math.BigDecimal("50.00"));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestService.CreateRequestResult result = requestService.createPendingRequestIfAbsent(
                null, payload, RequestType.UPDATE_BALANCE_OPTIMISTIC, CLIENT_ID);

        assertThat(result.alreadyExisted()).isFalse();
        assertThat(result.requestId()).isNotNull();
        verify(requestRepository, never()).findById(any(RequestId.class));
        verify(requestRepository).save(any(Request.class));
    }
}
