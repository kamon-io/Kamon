package kamon;

import kamon.akka.pingpong.PingPong;
import kamon.annotation.KamonController;
import org.springframework.boot.SpringApplication;

public class KamonSpringApplication {
	public static void main(String... args) {
	  Kamon.start();
	  SpringApplication.run(KamonController.class, args);
	}
}
