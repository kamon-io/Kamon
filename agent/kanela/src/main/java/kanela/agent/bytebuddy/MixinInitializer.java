package kanela.agent.bytebuddy;

import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.jar.asm.commons.AdviceAdapter;
import net.bytebuddy.jar.asm.commons.Method;
import net.bytebuddy.utility.OpenedClassReader;

public class MixinInitializer extends AdviceAdapter {
  private static final String ConstructorDescriptor = "<init>";

  private final Type typeClass;
  private final String initializerMethodName;
  private boolean cascadingConstructor;

  MixinInitializer(
      MethodVisitor mv,
      int access,
      String name,
      String desc,
      Type typeClass,
      String initializerMethodName) {
    super(OpenedClassReader.ASM_API, mv, access, name, desc);
    this.typeClass = typeClass;
    this.initializerMethodName = initializerMethodName;
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
    if (name.equals(ConstructorDescriptor) && owner.equals(typeClass.getInternalName()))
      cascadingConstructor = true;
    super.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  protected void onMethodExit(int opcode) {
    if (!cascadingConstructor) {
      loadThis();
      invokeVirtual(typeClass, new Method(initializerMethodName, "()V"));
    }
  }
}
