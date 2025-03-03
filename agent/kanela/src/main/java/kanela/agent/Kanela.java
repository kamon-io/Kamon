package kanela.agent;

import static java.util.Comparator.comparing;
import static kanela.agent.bytebuddy.ClassLoaderClassMatcher.*;
import static kanela.agent.bytebuddy.ScalaCompilerClassLoaderMatcher.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import kanela.agent.api.instrumentation.InstrumentationBuilder;
import kanela.agent.api.instrumentation.InstrumentationBuilder.Target;
import kanela.agent.bytebuddy.AdviceExceptionHandler;
import kanela.agent.bytebuddy.BridgeClassVisitorWrapper;
import kanela.agent.bytebuddy.MixinClassVisitorWrapper;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.ResubmissionScheduler;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.pmw.tinylog.Logger;

public class Kanela {
  private static volatile Instrumentation currentJvmInstrumentation;
  private static volatile ClassLoader instrumentationClassLoader;
  private static final String InstrumentationClassLoaderPropertyName =
      "kanela.instrumentation.classLoader";
  private static final String LoadedIndicatorPropertyName = "kanela.loaded";

  public static void premain(String args, Instrumentation instrumentation) {
    start(args, instrumentation, false);
  }

  public static void agentmain(String args, Instrumentation instrumentation) {
    start(args, instrumentation, true);
  }

  private static void start(
      String args, Instrumentation instrumentation, boolean isAttachingAtRuntime) {
    if (currentJvmInstrumentation == null) {
      currentJvmInstrumentation = instrumentation;
      instrumentationClassLoader = findInstrumentationClassloader();

      var previousClassLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(instrumentationClassLoader);

        // Ensures some critical classes are loaded before we start the instrumentation,
        // so that they don't get loaded in the middle of changing some other class.
        PreInit.loadKnownRequiredClasses(instrumentationClassLoader);

        var configuration = Configuration.createFrom(instrumentationClassLoader);
        var moduleTargets = loadModuleTargets(configuration, instrumentationClassLoader);
        var executor = Executors.newScheduledThreadPool(1);
        executor.submit(() -> Thread.currentThread().setName("kanela-scheduled-executor"));

        installByteBuddyAgent(
            configuration,
            isAttachingAtRuntime,
            executor,
            instrumentation,
            instrumentationClassLoader,
            moduleTargets);

        Logger.debug("Kanela agent installed");

      } catch (Throwable e) {
        // TODO: Better error signaling
        e.printStackTrace();
      } finally {
        Thread.currentThread().setContextClassLoader(previousClassLoader);
      }
    }
  }

  /**
   * Figures out what ClassLoader should be used for finding instrumentation classes. Most of the
   * time all the Instrumentation classes are loaded from the system classloader but in some cases,
   * like when running from SBT or PlayFramework, the instrumentation classes are isolated in a
   * different ClassLoader that we can "expose" to Kanela through the
   * `kanela.instrumentation.classLoader` system property. (Yes, we know that system properties are
   * not meant to be used like this).
   */
  private static ClassLoader findInstrumentationClassloader() {
    var customClassLoader = System.getProperties().get(InstrumentationClassLoaderPropertyName);
    if (customClassLoader != null && customClassLoader instanceof ClassLoader) {
      return (ClassLoader) customClassLoader;
    } else {
      return ClassLoader.getSystemClassLoader();
    }
  }

  static record ModuleTargets(
      Configuration.Module moduleConfig, List<InstrumentationBuilder.Target> targets) {}

  private static List<ModuleTargets> loadModuleTargets(
      Configuration configuration, ClassLoader instrumentationClassLoader) {

    return configuration.modules().stream()
        .filter(Configuration.Module::enabled)
        .sorted(comparing(Configuration.Module::order).thenComparing(Configuration.Module::name))
        .map(
            moduleConfig -> {
              var targets =
                  moduleConfig.instrumentations().stream()
                      .flatMap(
                          instrumentationName -> {
                            try {
                              var instrumentation =
                                  Class.forName(
                                          instrumentationName, true, instrumentationClassLoader)
                                      .getConstructor()
                                      .newInstance();

                              return ((InstrumentationBuilder) instrumentation).targets().stream();

                            } catch (Exception e) {
                              Logger.error(e, "Failed to instantiate {}", instrumentationName);
                              return Stream.empty();
                            }
                          });

              return new ModuleTargets(moduleConfig, targets.toList());
            })
        .toList();
  }

  private static void installByteBuddyAgent(
      Configuration config,
      boolean isAttachingAtRuntime,
      ScheduledExecutorService executor,
      Instrumentation instrumentation,
      ClassLoader instrumentationClassLoader,
      List<ModuleTargets> modules) {

    ConcurrentMap<? super ClassLoader, TypePool.CacheProvider> caches = new ConcurrentHashMap<>();

    // Caches must be cleared manually to avoid memory leaks and most of the information in
    // the type pools can be safely discarded after the instrumentation is done.
    executor.scheduleAtFixedRate(
        () -> {
          caches.forEach((key, value) -> value.clear());
        },
        10,
        10,
        TimeUnit.SECONDS);

    var byteBuddy =
        new ByteBuddy()
            .with(TypeValidation.ENABLED)
            .with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE);

    var resubmission = new ResubmissionScheduler.WithFixedDelay(executor, 10, TimeUnit.SECONDS);
    var poolStrategy = new AgentBuilder.PoolStrategy.WithTypePoolCache.Simple(caches);
    var agentBuilder =
        new AgentBuilder.Default(byteBuddy)
            .with(poolStrategy)
            .with(new DebugInstrumentationListener());

    agentBuilder =
        agentBuilder
            .ignore(any(), isExtensionClassLoader())
            .or(any(), isGroovyClassLoader())
            .or(any(), isSBTClassLoader())
            .or(any(), isSBTPluginClassLoader())
            .or(any(), isSBTScalaCompilerClassLoader())
            .or(any(), isSBTCachedClassLoader())
            .or(any(), isLagomClassLoader())
            .or(any(), isLagomServiceLocatorClassLoader())
            .or(any(), isReflectionClassLoader());
    // TODO: Should we exclude all the matches?

    if (isAttachingAtRuntime) {
      agentBuilder =
          agentBuilder
              .disableClassFormatChanges()
              .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
              .withResubmission(resubmission)
              .resubmitOnError();
    }

    for (ModuleTargets module : modules) {
      for (Target target : module.targets()) {

        for (Target.Advice advice : target.advisors()) {
          var adviceTransformer =
              new AgentBuilder.Transformer.ForAdvice()
                  .advice(advice.method(), advice.implementationClassName())
                  .withExceptionHandler(AdviceExceptionHandler.instance())
                  .include(instrumentationClassLoader);

          agentBuilder = agentBuilder.type(target.typeMatcher()).transform(adviceTransformer);
        }

        for (Target.Interceptor interceptor : target.interceptors()) {
          var methodDelegation =
              interceptor.eitherObject() != null
                  ? MethodDelegation.to(interceptor.eitherObject())
                  : MethodDelegation.to(interceptor.orImplementation());

          var interceptorTransformer =
              new AgentBuilder.Transformer() {

                @Override
                public DynamicType.Builder transform(
                    DynamicType.Builder builder,
                    TypeDescription typeDescription,
                    ClassLoader classloader,
                    JavaModule module,
                    ProtectionDomain protectionDomain) {

                  return builder.method(interceptor.method()).intercept(methodDelegation);
                }
              };

          agentBuilder = agentBuilder.type(target.typeMatcher()).transform(interceptorTransformer);
        }

        for (Class<?> mixinClass : target.mixinClasses()) {
          var mixinTranformer =
              new AgentBuilder.Transformer() {

                @Override
                public DynamicType.Builder transform(
                    DynamicType.Builder builder,
                    TypeDescription typeDescription,
                    ClassLoader classloader,
                    JavaModule module,
                    ProtectionDomain protectionDomain) {

                  var interfaces =
                      Arrays.asList(mixinClass.getInterfaces()).stream()
                          .distinct()
                          .map(TypeDescription.ForLoadedType::of)
                          .toArray(size -> new TypeDescription[size]);

                  return builder
                      .implement(interfaces)
                      .visit(new MixinClassVisitorWrapper(mixinClass, instrumentationClassLoader));
                }
              };

          agentBuilder = agentBuilder.type(target.typeMatcher()).transform(mixinTranformer);
        }

        for (Class<?> bridgeInterface : target.bridgeInterfaces()) {
          var bridgeTransformer =
              new AgentBuilder.Transformer() {

                @Override
                public DynamicType.Builder transform(
                    DynamicType.Builder builder,
                    TypeDescription typeDescription,
                    ClassLoader classloader,
                    JavaModule module,
                    ProtectionDomain protectionDomain) {

                  return builder
                      .implement(TypeDescription.ForLoadedType.of(bridgeInterface))
                      .visit(new BridgeClassVisitorWrapper(bridgeInterface));
                }
              };

          agentBuilder = agentBuilder.type(target.typeMatcher()).transform(bridgeTransformer);
        }

        for (AgentBuilder.Transformer transformer : target.transformers()) {
          agentBuilder = agentBuilder.type(target.typeMatcher()).transform(transformer);
        }
      }
    }

    agentBuilder.installOn(instrumentation);
  }

  public static class DebugInstrumentationListener extends AgentBuilder.Listener.Adapter {
    public void onError(
        String typeName,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        Throwable throwable) {
      throwable.printStackTrace();
    }
  }
}
