package kamon.netty.instrumentation.advisor;

import io.netty.channel.EventLoop;
import java.util.Queue;
import kamon.netty.util.MonitoredQueue;
import kanela.agent.libs.net.bytebuddy.asm.Advice.OnMethodExit;
import kanela.agent.libs.net.bytebuddy.asm.Advice.Return;
import kanela.agent.libs.net.bytebuddy.asm.Advice.This;

public class NewTaskQueueMethodAdvisor {

  @OnMethodExit
  static void onExit(@This Object eventLoop, @Return(readOnly = false) Queue<Runnable> queue) {
    MonitoredQueue.apply((EventLoop) eventLoop, queue);
  }
}
