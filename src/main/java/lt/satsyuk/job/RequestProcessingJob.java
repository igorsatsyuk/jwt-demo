package lt.satsyuk.job;

import lt.satsyuk.service.RequestProcessingService;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.UUID;

public class RequestProcessingJob extends QuartzJobBean {

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String AUTH_CLIENT_ID_KEY = "authClientId";

    private final RequestProcessingService requestProcessingService;

    public RequestProcessingJob(RequestProcessingService requestProcessingService) {
        this.requestProcessingService = requestProcessingService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String requestId = jobDataMap.getString(REQUEST_ID_KEY);
        String authClientId = jobDataMap.getString(AUTH_CLIENT_ID_KEY);
        if (requestId == null || requestId.isBlank()) {
            throw new JobExecutionException("Missing requestId in Quartz job data");
        }
        if (authClientId == null || authClientId.isBlank()) {
            throw new JobExecutionException("Missing authClientId in Quartz job data");
        }

        requestProcessingService.processClientCreateRequest(UUID.fromString(requestId), authClientId);
    }
}
