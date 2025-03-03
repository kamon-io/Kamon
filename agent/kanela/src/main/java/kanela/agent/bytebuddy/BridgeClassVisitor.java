package kanela.agent.bytebuddy;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import kanela.agent.api.instrumentation.bridge.Bridge;
import kanela.agent.api.instrumentation.bridge.FieldBridge;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.jar.asm.commons.Method;
import net.bytebuddy.utility.OpenedClassReader;

public class BridgeClassVisitor extends ClassVisitor {

  private final Class<?> bridgeInterface;
  private final Type type;

  public BridgeClassVisitor(Class<?> bridgeInterface, String className, ClassVisitor classVisitor) {
    super(OpenedClassReader.ASM_API, classVisitor);
    this.bridgeInterface = bridgeInterface;
    this.type = Type.getObjectType(className);
  }

  @Override
  public void visitEnd() {
    Arrays.asList(bridgeInterface.getDeclaredMethods()).stream()
        .filter(
            method ->
                (method.isAnnotationPresent(Bridge.class)
                    || method.isAnnotationPresent(FieldBridge.class)))
        .distinct()
        .forEach(
            reflectMethod -> {
              for (Annotation annotation : reflectMethod.getDeclaredAnnotations()) {
                if (annotation instanceof Bridge) processBridge(reflectMethod, annotation);
                if (annotation instanceof FieldBridge)
                  processFieldBridge(reflectMethod, annotation);
              }
            });
    cv.visitEnd();
  }

  private void processBridge(java.lang.reflect.Method reflectMethod, Annotation annotation) {
    var bridge = (Bridge) annotation;
    var method = Method.getMethod(reflectMethod);
    var targetMethod = Method.getMethod(bridge.value());

    var mv =
        cv.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, null);
    mv.visitCode();
    int i = 0;
    mv.visitVarInsn(Opcodes.ALOAD, i++);

    for (Type argument : method.getArgumentTypes()) {
      mv.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), i++);
    }

    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        type.getInternalName(),
        targetMethod.getName(),
        targetMethod.getDescriptor(),
        false);
    mv.visitInsn(method.getReturnType().getOpcode(Opcodes.IRETURN));
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void processFieldBridge(java.lang.reflect.Method reflectMethod, Annotation annotation) {
    var fieldBridge = (FieldBridge) annotation;
    var method = Method.getMethod(reflectMethod);

    var mv =
        cv.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, null);

    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitFieldInsn(
        Opcodes.GETFIELD,
        type.getInternalName(),
        fieldBridge.value(),
        method.getReturnType().getDescriptor());
    mv.visitInsn(Type.getType(type.getDescriptor()).getOpcode(Opcodes.IRETURN));
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }
}
