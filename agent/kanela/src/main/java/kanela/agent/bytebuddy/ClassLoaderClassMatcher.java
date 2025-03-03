package kanela.agent.bytebuddy;

import net.bytebuddy.matcher.ElementMatcher.Junction;

public class ClassLoaderClassMatcher extends Junction.AbstractBase<ClassLoader> {

  private final String classLoaderClassName;

  public ClassLoaderClassMatcher(String classLoaderClassName) {
    this.classLoaderClassName = classLoaderClassName;
  }

  @Override
  public boolean matches(ClassLoader target) {
    return target != null && classLoaderClassName.equals(target.getClass().getName());
  }

  public static Junction<ClassLoader> isReflectionClassLoader() {
    return new ClassLoaderClassMatcher("sun.reflect.DelegatingClassLoader");
  }

  public static Junction<ClassLoader> isGroovyClassLoader() {
    return new ClassLoaderClassMatcher("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader");
  }

  public static Junction<ClassLoader> isSBTClassLoader() {
    return new ClassLoaderClassMatcher("xsbt.boot.LibraryClassLoader");
  }

  public static Junction<ClassLoader> isSBTPluginClassLoader() {
    return new ClassLoaderClassMatcher("sbt.internal.PluginManagement$PluginClassLoader");
  }

  public static Junction<ClassLoader> isSBTCachedClassLoader() {
    return new ClassLoaderClassMatcher(
        "sbt.internal.classpath.ClassLoaderCache$Key$CachedClassLoader");
  }

  public static Junction<ClassLoader> isLagomClassLoader() {
    return new ClassLoaderClassMatcher("com.lightbend.lagom.dev.NamedURLClassLoader");
  }

  public static Junction<ClassLoader> isLagomServiceLocatorClassLoader() {
    return new ClassLoaderClassMatcher(
        "com.lightbend.lagom.sbt.SbtKanelaRunnerLagom$LagomServiceLocatorClassLoader");
  }
}
