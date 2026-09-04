package testapp.code;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController

public class ClientTestController {

    private final String baseUrl = "https://devnull-as-a-service.com/dev/null";

    private final WebClient client = WebClient.create(baseUrl);

    @GetMapping("/traceID")
    public String trace(HttpServletRequest request) {
        return request.getHeader("x-b3-traceid");
    }

    @GetMapping("/spanID")
    public String span(HttpServletRequest request) {
        return request.getHeader("x-b3-spanid");
    }
}
