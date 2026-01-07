package lt.satsyuk.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/user")
    @PreAuthorize("hasAuthority('USER')")
    public String user() {
        return "user endpoint";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ADMIN')")
    public String admin() {
        return "admin endpoint";
    }
}