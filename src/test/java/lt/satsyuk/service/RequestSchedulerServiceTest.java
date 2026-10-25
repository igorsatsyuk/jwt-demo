package lt.satsyuk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestSchedulerServiceTest {

    private final Scheduler scheduler = mock(Scheduler.class);
    private final RequestSchedulerService service = new RequestSchedulerService(scheduler);

    @Test
    void schedulesJobWithRequestAndClientIdentifiers() throws Exception {
        UUID requestId = UUID.randomUUID();

        service.scheduleClientCreateRequest(requestId, "spring-app");

        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @ParameterizedTest
    @NullSource
    void rejectsNullRequestId(UUID requestId) {
        assertThatThrownBy(() -> service.scheduleClientCreateRequest(requestId, "spring-app"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("requestId must not be null");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    void rejectsNullOrBlankAuthClientId(String authClientId) {
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> service.scheduleClientCreateRequest(requestId, authClientId))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }
}
