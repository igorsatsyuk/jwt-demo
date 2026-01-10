package lt.satsyuk.api;

import lt.satsyuk.api.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<String> user() {
        return ApiResponse.ok("user endpoint");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> admin() {
        return ApiResponse.ok("admin endpoint");
    }
}