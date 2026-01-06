package lt.satsyuk.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProtectedController {

    @GetMapping("/protected")
    public String protectedEndpoint() {
        return "protected content";
    }

    @GetMapping("/admin")
    public String adminEndpoint() {
        return "admin content";
    }
}