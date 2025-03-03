package kanela.agent;

import java.util.Collections;
import java.util.LinkedList;

class PreInit {

  private static String[] javaTypes = {
    "java.util.concurrent.locks.LockSupport",
    "java.util.concurrent.ThreadLocalRandom",
    "java.util.concurrent.locks.AbstractQueuedSynchronizer$Node"
  };

  private static String[] byteBuddyTypes = {
    "net.bytebuddy.agent.builder.ResettableClassFileTransformer",
    "net.bytebuddy.agent.ByteBuddyAgent",
    "net.bytebuddy.asm.Advice",
    "net.bytebuddy.asm.AsmVisitorWrapper",
    "net.bytebuddy.ByteBuddy",
    "net.bytebuddy.ClassFileVersion",
    "net.bytebuddy.description.ByteCodeElement",
    "net.bytebuddy.description.field.FieldDescription",
    "net.bytebuddy.description.field.FieldList",
    "net.bytebuddy.description.method.MethodDescription",
    "net.bytebuddy.description.method.MethodList",
    "net.bytebuddy.description.NamedElement",
    "net.bytebuddy.description.type.TypeDescription",
    "net.bytebuddy.dynamic.ClassFileLocator",
    "net.bytebuddy.dynamic.DynamicType",
    "net.bytebuddy.dynamic.loading.ClassInjector",
    "net.bytebuddy.dynamic.scaffold.MethodGraph",
    "net.bytebuddy.dynamic.scaffold.TypeValidation",
    "net.bytebuddy.implementation.bytecode.StackManipulation",
    "net.bytebuddy.implementation.Implementation",
    "net.bytebuddy.implementation.MethodDelegation",
    "net.bytebuddy.jar.asm.ClassReader",
    "net.bytebuddy.jar.asm.ClassVisitor",
    "net.bytebuddy.jar.asm.ClassWriter",
    "net.bytebuddy.jar.asm.commons.AdviceAdapter",
    "net.bytebuddy.jar.asm.commons.ClassRemapper",
    "net.bytebuddy.jar.asm.commons.Method",
    "net.bytebuddy.jar.asm.commons.MethodRemapper",
    "net.bytebuddy.jar.asm.commons.SimpleRemapper",
    "net.bytebuddy.jar.asm.Label",
    "net.bytebuddy.jar.asm.MethodVisitor",
    "net.bytebuddy.jar.asm.Opcodes",
    "net.bytebuddy.jar.asm.tree.ClassNode",
    "net.bytebuddy.jar.asm.tree.MethodNode",
    "net.bytebuddy.jar.asm.Type",
    "net.bytebuddy.matcher.ElementMatcher",
    "net.bytebuddy.matcher.ElementMatchers",
    "net.bytebuddy.pool.TypePool",
    "net.bytebuddy.utility.JavaModule",
    "net.bytebuddy.utility.OpenedClassReader",
  };

  public static void loadKnownRequiredClasses(ClassLoader classLoader)
      throws ClassNotFoundException {
    var classesToLoad = new LinkedList<String>();
    Collections.addAll(classesToLoad, javaTypes);
    Collections.addAll(classesToLoad, byteBuddyTypes);

    for (String type : classesToLoad) {
      Class.forName(type, true, classLoader);
    }
  }
}
