package kamon.akka.pingpong;

import akka.actor.UntypedActor;

class Ponger extends UntypedActor {
    static final class PongMessage {}

    public void onReceive(Object message) throws Exception {
        if (message instanceof Pinger.PingMessage) getSender().tell(new PongMessage(), getSelf());
        else unhandled(message);
    }
}
