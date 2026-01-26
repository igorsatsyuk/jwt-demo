package lt.satsyuk.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @AllArgsConstructor
    @Getter
    public enum ErrorCode {
        BAD_REQUEST(40001, "Bad request"),
        UNAUTHORIZED(40101, "Unauthorized"),
        INVALID_GRANT(40102, "Invalid grant"),
        INVALID_TOKEN(40103, "Invalid token"),
        FORBIDDEN(40301, "Forbidden"),
        INTERNAL_SERVER_ERROR(50000, "Internal server error");

        private final int code;
        private final String description;
    }

    private final int code;
    private final T data;
    private final String message;

    @JsonCreator
    public ApiResponse(
            @JsonProperty("code") int code,
            @JsonProperty("data") T data,
            @JsonProperty("message") String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, data, "OK");
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, null, message);
    }
}