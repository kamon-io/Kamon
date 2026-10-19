package testapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

@SpringBootApplication
public class TestApp {
    public static void main(String[] args) {
        kamon.Kamon.init();
        final SpringApplication app = new SpringApplication(TestApp.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", args[0]));
        app.run(args);
    }
}
