package testapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestApp {
    public static void main(String[] args) {
        kamon.Kamon.init();
        SpringApplication.run(TestApp.class, args);
    }
}
