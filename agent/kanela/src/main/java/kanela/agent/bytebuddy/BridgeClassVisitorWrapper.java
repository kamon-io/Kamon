package kanela.agent.bytebuddy;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.pool.TypePool;

public class BridgeClassVisitorWrapper extends AsmVisitorWrapper.AbstractBase {

  private final Class<?> bridgeInterface;

  public BridgeClassVisitorWrapper(Class<?> bridgeInterface) {
    this.bridgeInterface = bridgeInterface;
  }

  @Override
  public int mergeWriter(int flags) {
    return flags | ClassWriter.COMPUTE_FRAMES;
  }

  @Override
  public int mergeReader(int flags) {
    return flags | ClassReader.EXPAND_FRAMES;
  }

  @Override
  public ClassVisitor wrap(
      TypeDescription instrumentedType,
      ClassVisitor classVisitor,
      Implementation.Context implementationContext,
      TypePool typePool,
      FieldList<FieldDescription.InDefinedShape> fields,
      MethodList<?> methods,
      int writerFlags,
      int readerFlags) {

    return new BridgeClassVisitor(
        bridgeInterface, instrumentedType.getInternalName(), classVisitor);
  }
}
