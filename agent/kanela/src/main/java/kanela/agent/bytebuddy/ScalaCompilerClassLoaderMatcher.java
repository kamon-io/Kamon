package kanela.agent.bytebuddy;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.matcher.ElementMatcher.Junction;

public class ScalaCompilerClassLoaderMatcher extends Junction.AbstractBase<ClassLoader> {

  private static Map<ClassLoader, Boolean> knownClassLoaders = new ConcurrentHashMap<>();

  /**
   * Tries to determine whether a ClassLoader is the Scala Compiler ClassLoader on SBT. Since there
   * is no special naming or treatment of this particular ClassLoader it is impossible to have a
   * 100% reliable way to filter it out from the instrumentation process, but given that the jars
   * found on it are quite particular (the compiler and jline) and there are usually just a handful
   * of jars in that ClassLoader, we can have a level of certainty that if a ClassLoader has less
   * than 6 jars and some of those are the compiler-related libraries then it must be the compiler
   * ClassLoader.
   *
   * <p>We are doing this check here instead of using a ClassLoaderNameMatcher because this is a
   * relatively expensive check which might only be necessary in a few cases, so we rather filter
   * the error than putting the burden of this check on every single class load.
   */
  @Override
  public boolean matches(ClassLoader classLoader) {
    if (classLoader instanceof URLClassLoader) {
      Boolean isScalaCompilerLoader = knownClassLoaders.get(classLoader);

      if (isScalaCompilerLoader != null) return isScalaCompilerLoader;
      else {
        var urlClassLoader = (URLClassLoader) classLoader;
        boolean foundScalaCompiler = false;
        boolean foundJLine = false;
        boolean hasLessThanSixJars = urlClassLoader.getURLs().length < 6;

        if (hasLessThanSixJars) {
          for (URL url : urlClassLoader.getURLs()) {
            if (url.getFile().contains("scala-compiler")) foundScalaCompiler = true;

            if (url.getFile().contains("jline")) foundJLine = true;
          }
        }

        var isScalaCompiler = hasLessThanSixJars && foundScalaCompiler && foundJLine;
        knownClassLoaders.put(classLoader, isScalaCompiler);
        return isScalaCompiler;
      }
    } else return false;
  }

  public static Junction<ClassLoader> isSBTScalaCompilerClassLoader() {
    return new ScalaCompilerClassLoaderMatcher();
  }
}
