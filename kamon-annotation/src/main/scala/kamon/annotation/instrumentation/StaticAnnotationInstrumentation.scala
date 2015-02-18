package kamon.annotation.instrumentation

import java.lang.reflect.Modifier

import kamon.annotation.util.{ EnhancedELProcessor, ELProcessorPool }
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.{ After, Aspect }

@Aspect
class StaticAnnotationInstrumentation extends BaseAnnotationInstrumentation {

  @After("staticinitialization(@kamon.annotation.Metrics *)")
  def creation(jps: JoinPoint.StaticPart): Unit = {

    import EnhancedELProcessor.Syntax

    val clazz = jps.getSignature.getDeclaringType

    val stringEvaluator: StringEvaluator = (str: String) ⇒ ELProcessorPool.useWithClass(clazz)(_.evalToString(str))
    val tagsEvaluator: TagsEvaluator = (str: String) ⇒ ELProcessorPool.use(_.evalToMap(str))

    clazz.getDeclaredMethods.filter(method ⇒ Modifier.isStatic(method.getModifiers) && !method.isSynthetic).foreach {
      method ⇒
        registerTrace(method, StaticAnnotationInstrumentation.traces, stringEvaluator, tagsEvaluator)
        registerSegment(method, StaticAnnotationInstrumentation.segments, stringEvaluator, tagsEvaluator)
        registerCounter(method, StaticAnnotationInstrumentation.counters, stringEvaluator, tagsEvaluator)
        registerMinMaxCounter(method, StaticAnnotationInstrumentation.minMaxCounters, stringEvaluator, tagsEvaluator)
        registerHistogram(method, StaticAnnotationInstrumentation.histograms, stringEvaluator, tagsEvaluator)
        registerTime(method, StaticAnnotationInstrumentation.histograms, stringEvaluator, tagsEvaluator)
    }
  }
}

case object StaticAnnotationInstrumentation extends Profiled