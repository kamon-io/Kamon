package kamon.annotation;

import kamon.Kamon;
import org.springframework.boot.SpringApplication;

public class KamonSpringApplication {
	public static void main(String... args) {
	  Kamon.start();
	  SpringApplication.run(KamonController.class,args);
	}
}
