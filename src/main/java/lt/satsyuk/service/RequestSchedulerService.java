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
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(RequestProcessingJob.REQUEST_ID_KEY, requestId.toString());
        jobDataMap.put(RequestProcessingJob.AUTH_CLIENT_ID_KEY, authClientId);

        String jobIdentity = requestId + "|" + authClientId;

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
