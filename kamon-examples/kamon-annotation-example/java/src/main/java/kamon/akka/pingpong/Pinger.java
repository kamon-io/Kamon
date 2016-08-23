package kamon.akka.pingpong;

import akka.actor.UntypedActor;

class Pinger extends UntypedActor {
    static final class PingMessage {}

    public void onReceive(Object message) throws Exception {
        if (message instanceof Ponger.PongMessage) getSender().tell(new PingMessage(), getSelf());
        else unhandled(message);
    }
}
