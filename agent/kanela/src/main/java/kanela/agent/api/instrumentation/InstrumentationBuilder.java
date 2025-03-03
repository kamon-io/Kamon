package kanela.agent.api.instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.matcher.ElementMatchers;

public abstract class InstrumentationBuilder {
  private final List<Target.Builder> targetBuilders = new LinkedList<>();

  protected final ElementMatcher.Junction<ByteCodeElement> notDeclaredByObject =
      not(isDeclaredBy(Object.class));

  protected final ElementMatcher.Junction<MethodDescription> notTakesArguments =
      not(takesArguments(0));

  private static ElementMatcher.Junction<TypeDescription> defaultTypeMatcher =
      not(isInterface()).and(not(isSynthetic()));

  public List<Target> targets() {
    return targetBuilders.stream()
        .map(
            builder ->
                new Target(
                    builder.typeMatcher,
                    builder.bridgeInterfaces,
                    builder.mixinClasses,
                    builder.transformers,
                    builder.advisors,
                    builder.interceptors))
        .toList();
  }

  public Target.Builder onType(String typeName) {
    var target = new Target.Builder(named(typeName));
    targetBuilders.add(target);
    return target;
  }

  public Target.Builder onTypes(String... typeName) {
    var target = new Target.Builder(anyTypes(typeName));
    targetBuilders.add(target);
    return target;
  }

  public Target.Builder onSubTypesOf(String... typeName) {
    var target = new Target.Builder(defaultTypeMatcher.and(hasSuperType(anyTypes(typeName))));
    targetBuilders.add(target);
    return target;
  }

  public Target.Builder onTypesAnnotatedWith(String annotationName) {
    var target = new Target.Builder(defaultTypeMatcher.and(isAnnotatedWith(named(annotationName))));
    targetBuilders.add(target);
    return target;
  }

  public Target.Builder onTypesWithMethodsAnnotatedWith(String annotationName) {
    final Junction<MethodDescription> methodMatcher = isAnnotatedWith(named(annotationName));
    var target =
        new Target.Builder(defaultTypeMatcher.and(hasSuperType(declaresMethod(methodMatcher))));
    targetBuilders.add(target);
    return target;
  }

  public Target.Builder onTypesMatching(ElementMatcher<? super TypeDescription> typeMatcher) {
    var target = new Target.Builder(defaultTypeMatcher.and(failSafe(typeMatcher)));
    targetBuilders.add(target);
    return target;
  }

  public ElementMatcher.Junction<MethodDescription> method(String name) {
    return named(name);
  }

  public ElementMatcher.Junction<MethodDescription> isConstructor() {
    return ElementMatchers.isConstructor();
  }

  public ElementMatcher.Junction<MethodDescription> isAbstract() {
    return ElementMatchers.isAbstract();
  }

  public ElementMatcher.Junction<TypeDescription> anyTypes(String... names) {
    return Arrays.stream(names)
        .map(name -> ElementMatchers.<TypeDescription>named(name))
        .reduce(Junction::or)
        .orElse(ElementMatchers.none());
  }

  public ElementMatcher.Junction<MethodDescription> takesArguments(Integer quantity) {
    return ElementMatchers.takesArguments(quantity);
  }

  public ElementMatcher.Junction<MethodDescription> takesOneArgumentOf(String type) {
    return ElementMatchers.takesArgument(0, named(type));
  }

  public ElementMatcher.Junction<MethodDescription> withArgument(Integer index, Class<?> type) {
    return ElementMatchers.takesArgument(index, type);
  }

  public ElementMatcher.Junction<MethodDescription> withArgument(Class<?> type) {
    return withArgument(0, type);
  }

  public ElementMatcher.Junction<MethodDescription> anyMethods(String... names) {
    return Arrays.stream(names)
        .map(this::method)
        .reduce(ElementMatcher.Junction::or)
        .orElse(ElementMatchers.none());
  }

  public ElementMatcher.Junction<MethodDescription> withReturnTypes(Class<?>... types) {
    return Arrays.stream(types)
        .map(ElementMatchers::returns)
        .reduce(ElementMatcher.Junction::or)
        .orElse(ElementMatchers.none());
  }

  public ElementMatcher.Junction<MethodDescription> methodAnnotatedWith(String annotation) {
    return ElementMatchers.isAnnotatedWith(named(annotation));
  }

  public ElementMatcher.Junction<MethodDescription> methodAnnotatedWith(
      Class<? extends Annotation> annotation) {
    return ElementMatchers.isAnnotatedWith(annotation);
  }

  // TODO: Handle class refiners
  //
  // public ClassRefiner.Builder classIsPresent(String className) {
  //   return ClassRefiner.builder().mustContain(className);
  // }

  /** Marker interface for Scala companion objects that hold advice methods */
  public interface AdviceCompanion {}

  public static record Target(
      ElementMatcher<TypeDescription> typeMatcher,
      List<Class<?>> bridgeInterfaces,
      List<Class<?>> mixinClasses,
      List<AgentBuilder.Transformer> transformers,
      List<Advice> advisors,
      List<Interceptor> interceptors) {

    public static record Advice(
        ElementMatcher<MethodDescription> method, String implementationClassName) {}

    public static record Interceptor(
        ElementMatcher<MethodDescription> method, Object eitherObject, Class<?> orImplementation) {}

    public static class Builder {
      private final ElementMatcher<TypeDescription> typeMatcher;
      private final List<Class<?>> bridgeInterfaces = new LinkedList<>();
      private final List<Class<?>> mixinClasses = new LinkedList<>();
      private final List<AgentBuilder.Transformer> transformers = new LinkedList<>();
      private final List<Advice> advisors = new LinkedList<>();
      private final List<Interceptor> interceptors = new LinkedList<>();

      public Builder(ElementMatcher<TypeDescription> typeMatcher) {
        this.typeMatcher = typeMatcher;
      }

      public Target.Builder mixin(Class<?> implementation) {
        mixinClasses.add(implementation);
        return this;
      }

      public Target.Builder bridge(Class<?> implementation) {
        bridgeInterfaces.add(implementation);
        return this;
      }

      public Target.Builder advise(Junction<MethodDescription> method, Class<?> implementation) {
        advisors.add(new Advice(method, implementation.getName()));
        return this;
      }

      public Target.Builder advise(Junction<MethodDescription> method, String implementation) {
        advisors.add(new Advice(method, implementation));
        return this;
      }

      public Target.Builder advise(Junction<MethodDescription> method, AdviceCompanion companion) {
        // Scala companion object instances always have the '$' sign at the end of their class name,
        // we must remove it to get to the class that exposes the static methods.
        var adviceClassName = companion.getClass().getName();
        var cleanAdviceClassName = adviceClassName.substring(0, adviceClassName.length() - 1);
        advisors.add(new Advice(method, cleanAdviceClassName));
        return this;
      }

      public Target.Builder intercept(Junction<MethodDescription> method, Class<?> implementation) {
        interceptors.add(new Interceptor(method, null, implementation));
        return this;
      }

      public Target.Builder intercept(Junction<MethodDescription> method, Object implementation) {
        interceptors.add(new Interceptor(method, implementation, null));
        return this;
      }

      // TODO: Handle class refiners
      //
      //
      // public Target when(ClassRefiner.Builder... refinerBuilders) {
      //   val refiners =
      //       io.vavr.collection.List.of(refinerBuilders)
      //           .map(ClassRefiner.Builder::build)
      //           .toJavaArray(ClassRefiner[]::new);
      //   builder.withClassLoaderRefiner(() -> ClassLoaderRefiner.from(refiners));
      //   return this;
      // }
      //
      // public Target when(ClassRefiner... refiners) {
      //   builder.withClassLoaderRefiner(() -> ClassLoaderRefiner.from(refiners));
      //   return this;
      // }
    }
  }
}
