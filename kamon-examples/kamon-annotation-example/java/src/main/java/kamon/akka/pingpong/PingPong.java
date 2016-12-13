package kamon.akka.pingpong;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class PingPong {

    @PostConstruct
    public void initialize() {
        final ActorSystem system = ActorSystem.create("kamon-spring-boot-actor-system");

        final ActorRef pinger = system.actorOf(Props.create(Pinger.class), "pinger");
        final ActorRef ponger = system.actorOf(Props.create(Ponger.class), "ponger");

        pinger.tell(new Ponger.PongMessage(), ponger);
    }
}
