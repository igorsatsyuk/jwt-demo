package lt.satsyuk.api;

import lt.satsyuk.api.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<String> user() {
        log.info("Accessing user endpoint");
        return ApiResponse.ok("user endpoint");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> admin() {
        log.info("Accessing admin endpoint");
        return ApiResponse.ok("admin endpoint");
    }
}