package akka.actor.instrumentation;

import akka.actor.Cell;
import akka.actor.UnstartedCell;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class ReplaceWithAdvice {

    @Advice.OnMethodEnter()
    public static Cell enter(@Advice.Argument(value = 0, readOnly = false) Cell cell) {
        Cell originalCell = cell;
        cell = new CellWrapper(cell);
        return originalCell;
    }

    @Advice.OnMethodExit()
    public static void exit(@Advice.This UnstartedCell self, @Advice.Enter Cell originalCell) {
        self.self().swapCell(originalCell);
    }
}
