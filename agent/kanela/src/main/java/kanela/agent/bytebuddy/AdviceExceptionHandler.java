/*
 * =========================================================================================
 * Copyright © 2013-2021 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kanela.agent.bytebuddy;

import static net.bytebuddy.jar.asm.Opcodes.*;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

public class AdviceExceptionHandler extends Advice.ExceptionHandler.Simple {

  private static final AdviceExceptionHandler Instance =
      new AdviceExceptionHandler(getStackManipulation());

  private AdviceExceptionHandler(StackManipulation getStackForErrorManipulation) {
    super(getStackForErrorManipulation);
  }

  public static AdviceExceptionHandler instance() {
    return Instance;
  }

  /**
   * Produces the following bytecode:
   *
   * <pre>
   * } catch(Throwable throwable) {
   *     org.pmw.tinylog.Logger.error(throwable, "A suppressed exception was thrown while running advice")
   * }
   * </pre>
   */
  private static StackManipulation getStackManipulation() {
    return new StackManipulation() {

      @Override
      public boolean isValid() {
        return true;
      }

      @Override
      public Size apply(MethodVisitor methodVisitor, Implementation.Context implementationContext) {
        var endCatchBlock = new Label();
        // message
        methodVisitor.visitLdcInsn("A suppressed exception was thrown while running advice");
        // logger, message, throwable => throwable, message, logger
        // methodVisitor.visitInsn(Opcodes.SWAP);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "org/pmw/tinylog/Logger",
            "error",
            "(Ljava/lang/Throwable;Ljava/lang/String;)V",
            false);
        methodVisitor.visitJumpInsn(GOTO, endCatchBlock);
        // ending catch block
        methodVisitor.visitLabel(endCatchBlock);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

        return new StackManipulation.Size(-1, 1);
      }
    };
  }
}
