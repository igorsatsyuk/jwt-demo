package lt.satsyuk.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "request")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {

    @EmbeddedId
    private RequestId requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RequestStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "status_changed_at", nullable = false)
    private OffsetDateTime statusChangedAt;

    @Column(name = "request_data", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(read = "request_data::text", write = "?::jsonb")
    private String requestData;

    @Column(name = "response_data", columnDefinition = "jsonb")
    @ColumnTransformer(read = "response_data::text", write = "?::jsonb")
    private String responseData;

    public java.util.UUID getId() {
        return requestId != null ? requestId.getId() : null;
    }

    public String getAuthClientId() {
        return requestId != null ? requestId.getAuthClientId() : null;
    }

    public void markProcessing(OffsetDateTime changedAt) {
        this.status = RequestStatus.PROCESSING;
        this.statusChangedAt = changedAt;
    }

    public void markCompleted(String responseData, OffsetDateTime changedAt) {
        this.status = RequestStatus.COMPLETED;
        this.responseData = responseData;
        this.statusChangedAt = changedAt;
    }

    public void markFailed(String responseData, OffsetDateTime changedAt) {
        this.status = RequestStatus.FAILED;
        this.responseData = responseData;
        this.statusChangedAt = changedAt;
    }
}
