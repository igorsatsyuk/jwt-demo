package lt.satsyuk.service;

import lt.satsyuk.job.RequestProcessingJob;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class RequestSchedulerService {

    private static final String REQUEST_JOB_GROUP = "request-processing";
    private static final String REQUEST_TRIGGER_GROUP = "request-processing-trigger";

    private final Scheduler scheduler;

    public RequestSchedulerService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void scheduleClientCreateRequest(UUID requestId, String authClientId) throws SchedulerException {
        UUID nonNullRequestId = Objects.requireNonNull(requestId, "requestId must not be null");
        String nonNullAuthClientId = Objects.requireNonNull(authClientId, "authClientId must not be null");
        if (nonNullAuthClientId.isBlank()) {
            throw new IllegalArgumentException("authClientId must not be blank");
        }

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(RequestProcessingJob.REQUEST_ID_KEY, nonNullRequestId.toString());
        jobDataMap.put(RequestProcessingJob.AUTH_CLIENT_ID_KEY, nonNullAuthClientId);

        String jobIdentity = nonNullRequestId + "|" + nonNullAuthClientId;

        JobDetail jobDetail = JobBuilder.newJob(RequestProcessingJob.class)
                .withIdentity(jobIdentity, REQUEST_JOB_GROUP)
                .usingJobData(jobDataMap)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobIdentity, REQUEST_TRIGGER_GROUP)
                .forJob(jobDetail)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withRepeatCount(0)
                        .withMisfireHandlingInstructionFireNow())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }
}
