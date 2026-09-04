/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.annotation.instrumentation.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import kamon.Kamon;
import kamon.annotation.api.Time;
import kamon.annotation.api.TrackConcurrency;
import kamon.annotation.el.StringEvaluator;
import kamon.annotation.el.TagsEvaluator;
import kamon.metric.*;
import kamon.tag.TagSet;
import kamon.trace.SpanBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class AnnotationCache {

  private static Logger logger = LoggerFactory.getLogger(AnnotationCache.class);
  private static Map<MetricKey, Object> metrics = buildCache();

  private static Map<MetricKey, Object> buildCache() {
    return Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .removalListener(LogExpirationListener())
            .build()
            .asMap();
  }

  public static Gauge getGauge(Method method, Object obj, Class<?> clazz, String className, String methodName) {
    final MetricKey metricKey = MetricKey.from("Gauge", method, clazz);
    final kamon.annotation.api.Gauge gaugeAnnotation = method.getAnnotation(kamon.annotation.api.Gauge.class);

    final Gauge gauge = (Gauge) metrics.computeIfAbsent(metricKey, x -> Kamon.gauge(gaugeAnnotation.name()).withoutTags());

    if (containsELExpression(gaugeAnnotation.name(), gaugeAnnotation.tags())) {
      return (Gauge) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
        final String name = getOperationName(gaugeAnnotation.name(), obj, clazz, className, methodName);
        final Map<String, Object> tags = getTags(obj, clazz, gaugeAnnotation.tags());
        if (tags.isEmpty()) return Kamon.gauge(name).withoutTags();
        return Kamon.gauge(name).withTags(TagSet.from(tags));
      }));
    }
    return gauge;
  }

  public static Counter getCounter(Method method, Object obj, Class<?> clazz, String className, String methodName) {
    final MetricKey metricKey = MetricKey.from("Counter", method, clazz);
    final kamon.annotation.api.Count countAnnotation = method.getAnnotation(kamon.annotation.api.Count.class);
    final String counterMetricName = countAnnotation.name().equals("") ?
        className + "." + methodName :
        countAnnotation.name();

    final Counter counter = (Counter) metrics.computeIfAbsent(metricKey, x -> Kamon.counter(counterMetricName).withoutTags());

    if (containsELExpression(countAnnotation.name(), countAnnotation.tags())) {
      return (Counter) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
        final String name = getOperationName(counterMetricName, obj, clazz, className, methodName);
        final Map<String, Object> tags = getTags(obj, clazz, countAnnotation.tags());
        if (tags.isEmpty()) return Kamon.counter(name).withoutTags();
        return Kamon.counter(name).withTags(TagSet.from(tags));
      }));
    }
    return counter;
  }

  public static Histogram getHistogram(Method method, Object obj, Class<?> clazz, String className, String methodName) {
    final MetricKey metricKey = MetricKey.from("Histogram", method, clazz);
    final kamon.annotation.api.Histogram histogramAnnotation = method.getAnnotation(kamon.annotation.api.Histogram.class);

    final Histogram histogram = (Histogram) metrics.computeIfAbsent(metricKey, x -> getHistogram(histogramAnnotation, histogramAnnotation.name()).withoutTags());

    if (containsELExpression(histogramAnnotation.name(), histogramAnnotation.tags())) {
      return (Histogram) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
        final String name = getOperationName(histogramAnnotation.name(), obj, clazz, className, methodName);
        final Metric.Histogram histo = getHistogram(histogramAnnotation, name);
        final Map<String, Object> tags = getTags(obj, clazz, histogramAnnotation.tags());
        if (tags.isEmpty()) return histo.withoutTags();
        return histo.withTags(TagSet.from(tags));
      }));
    }
    return histogram;
  }


  public static RangeSampler getRangeSampler(Method method, Object obj, Class<?> clazz, String className, String methodName) {
    final MetricKey metricKey = MetricKey.from("Sampler", method, clazz);
    final TrackConcurrency trackConcurrencyAnnotation = method.getAnnotation(TrackConcurrency.class);
    final String rangeSamplerName = trackConcurrencyAnnotation.name().equals("") ?
        className + "." + methodName :
        trackConcurrencyAnnotation.name();

    final RangeSampler rangeSampler = (RangeSampler) metrics.computeIfAbsent(metricKey, x -> Kamon.rangeSampler(rangeSamplerName).withoutTags());

    if (containsELExpression(trackConcurrencyAnnotation.name(), trackConcurrencyAnnotation.tags())) {
      return (RangeSampler) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
        final String name = getOperationName(rangeSamplerName, obj, clazz, className, methodName);
        final Map<String, Object> tags = getTags(obj, clazz, trackConcurrencyAnnotation.tags());
        if (tags.isEmpty()) return Kamon.rangeSampler(name).withoutTags();
        return Kamon.rangeSampler(name).withTags(TagSet.from(tags));
      }));
    }
    return rangeSampler;
  }


  public static Timer getTimer(Method method, Object obj, Class<?> clazz, String className, String methodName) {
    final MetricKey metricKey = MetricKey.from("Time", method, clazz);
    final Time timeAnnotation = method.getAnnotation(Time.class);
    final String timerMetricName = timeAnnotation.name().equals("") ?
        className + "." + methodName :
        timeAnnotation.name();

    final Timer timer = (Timer) metrics.computeIfAbsent(metricKey, x -> Kamon.timer(timerMetricName).withoutTags());

    if (containsELExpression(timeAnnotation.name(), timeAnnotation.tags())) {
      return (Timer) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
        final String name = getOperationName(timerMetricName, obj, clazz, className, methodName);
        final Map<String, Object> tags = getTags(obj, clazz, timeAnnotation.tags());
        if (tags.isEmpty()) return Kamon.timer(name).withoutTags();
        return Kamon.timer(name).withTags(TagSet.from(tags));
      }));
    }
    return timer;
  }


  public static SpanBuilder getSpanBuilder(Method method, Object obj, Class<?> clazz, String className, String methodName) {
    final kamon.annotation.api.Trace traceAnnotation = method.getAnnotation(kamon.annotation.api.Trace.class);
    final String operationName = traceAnnotation.operationName().equals("") ?
      className + "." + methodName :
      traceAnnotation.operationName();

    // SpanBuilder cannot be cached as we do with metrics because the SpanBuilder instances are not thread safe and
    // the same method might be called from different threads.
    if (containsELExpression(traceAnnotation.operationName(), traceAnnotation.tags())) {
      final String name = getOperationName(operationName, obj, clazz, className, methodName);
      final SpanBuilder builder = resolveBuilder(name, traceAnnotation.component(), traceAnnotation.kind());;
      final Map<String, Object> tags = getTags(obj, clazz, traceAnnotation.tags());
      tags.forEach((k, v)-> builder.tag(k, v.toString()));
      return builder;

    } else {
      return resolveBuilder(operationName, traceAnnotation.component(), traceAnnotation.kind());
    }
  }

  private static SpanBuilder resolveBuilder(String operationName, String component, kamon.annotation.api.Trace.SpanKind spanKind) {
    switch (spanKind) {
      case Server:    return Kamon.serverSpanBuilder(operationName, component);
      case Client:    return Kamon.clientSpanBuilder(operationName, component);
      case Producer:  return Kamon.producerSpanBuilder(operationName, component);
      case Consumer:  return Kamon.consumerSpanBuilder(operationName, component);
      case Internal:  return Kamon.internalSpanBuilder(operationName, component);
      default:
        return Kamon.spanBuilder(operationName).tag("component", component);
    }
  }

  private static Metric.Histogram getHistogram(kamon.annotation.api.Histogram histogramAnnotation, String name) {
    return Kamon.histogram(name, MeasurementUnit.none(), new DynamicRange(histogramAnnotation.lowestDiscernibleValue(), histogramAnnotation.highestTrackableValue(), histogramAnnotation.precision()));
  }

  private static  Map<String, Object> getTags(Object obj, Class<?> clazz, String tags) {
    final Map<String, String> tagMap = (obj != null) ? TagsEvaluator.eval(obj, tags) : TagsEvaluator.eval(clazz, tags);
    return Collections.unmodifiableMap(tagMap);
  }

  private static String getOperationName(String name, Object obj, Class<?> clazz, String className, String methodName) {
    final String evaluatedString = (obj != null) ? StringEvaluator.evaluate(obj, name) : StringEvaluator.evaluate(clazz, name);
    return (evaluatedString.isEmpty() || evaluatedString.equals("unknown")) ? className + "." + methodName: evaluatedString;
  }

  private static RemovalListener<MetricKey, Object> LogExpirationListener() {
    return (key, value, cause) ->   {
      if(value instanceof Instrument) ((Instrument) value).remove();
      logger.debug("Expiring key: " + key + "with value" + value);
    };
  }

  private static boolean containsELExpression(String name, String tags) {
    return isEL(name) || isEL(tags);
  }

  private static boolean isEL(String str) {
    return str.contains("#{") || str.contains("${");
  }

  private static class MetricKey {
    private final String prefix;
    private final Method method;
    private final Class<?> clazz;

    public final Map<Object, Object> cache = new HashMap<>();

    private MetricKey(String prefix, Method method, Class<?> clazz) {
      this.prefix = prefix;
      this.method = method;
      this.clazz = clazz;
    }

    public static MetricKey from(String prefix, Method method, Class<?> clazz) {
      return new MetricKey(prefix, method, clazz);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final MetricKey metricKey = (MetricKey) o;
      return Objects.equals(prefix, metricKey.prefix) &&
          Objects.equals(method, metricKey.method) &&
          Objects.equals(clazz, metricKey.clazz);
    }

    @Override
    public int hashCode() {
      return Objects.hash(prefix, method, clazz);
    }
  }
}
