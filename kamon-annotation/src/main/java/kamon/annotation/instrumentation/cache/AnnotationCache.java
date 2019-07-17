/*
 * =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.annotation.instrumentation.cache;

import kamon.Kamon;
import kamon.annotation.el.StringEvaluator;
import kamon.annotation.el.TagsEvaluator;
import kamon.metric.*;
import kamon.tag.TagSet;
import kamon.trace.SpanBuilder;
import kanela.agent.libs.net.jodah.expiringmap.ExpirationListener;
import kanela.agent.libs.net.jodah.expiringmap.ExpirationPolicy;
import kanela.agent.libs.net.jodah.expiringmap.ExpiringMap;
import kanela.agent.util.log.Logger;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AnnotationCache {

    private static Map<MetricKey, Object> metrics = buildCache();

    private static Map<MetricKey, Object> buildCache() {
        return ExpiringMap
                .builder()
                .expiration(1, TimeUnit.MINUTES)
                .expirationPolicy(ExpirationPolicy.ACCESSED)
                .asyncExpirationListener(ExpirationListener())
                .build();
    }

    public static Gauge getGauge(Method method, Object obj, Class<?> clazz, String className, String methodName) {
        final MetricKey metricKey = MetricKey.from("Gauge", method, clazz);
        final kamon.annotation.api.Gauge gaugeAnnotation = method.getAnnotation(kamon.annotation.api.Gauge.class);

        final Gauge gauge = (Gauge) metrics.computeIfAbsent(metricKey, x -> Kamon.gauge(gaugeAnnotation.name()).withoutTags());

        if(containsELExpression(gaugeAnnotation.name(), gaugeAnnotation.tags())) {
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

        final Counter counter = (Counter) metrics.computeIfAbsent(metricKey, x -> Kamon.counter(countAnnotation.name()).withoutTags());

        if(containsELExpression(countAnnotation.name(), countAnnotation.tags())) {
            return (Counter) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
                final String name = getOperationName(countAnnotation.name(), obj, clazz, className, methodName);
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
                if(tags.isEmpty()) return histo.withoutTags();
                return histo.withTags(TagSet.from(tags));
            }));
        }
        return histogram;
    }


    public static RangeSampler getRangeSampler(Method method, Object obj, Class<?> clazz, String className, String methodName) {
        final MetricKey metricKey = MetricKey.from("Sampler", method, clazz);
        final kamon.annotation.api.RangeSampler rangeSamplerAnnotation = method.getAnnotation(kamon.annotation.api.RangeSampler.class);

        final RangeSampler rangeSampler = (RangeSampler) metrics.computeIfAbsent(metricKey, x -> Kamon.rangeSampler(rangeSamplerAnnotation.name()).withoutTags());

        if (containsELExpression(rangeSamplerAnnotation.name(), rangeSamplerAnnotation.tags())) {
            return (RangeSampler) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
                final String name = getOperationName(rangeSamplerAnnotation.name(), obj, clazz, className, methodName);
                final Map<String, Object> tags = getTags(obj, clazz, rangeSamplerAnnotation.tags());
                if(tags.isEmpty()) return Kamon.rangeSampler(name).withoutTags();
                return Kamon.rangeSampler(name).withTags(TagSet.from(tags));
            }));
        }
        return rangeSampler;
    }


    public static Timer getTimer(Method method, Object obj, Class<?> clazz, String className, String methodName) {
        final MetricKey metricKey = MetricKey.from("Timer", method, clazz);
        final kamon.annotation.api.Timer timerAnnotation = method.getAnnotation(kamon.annotation.api.Timer.class);

        final Timer timer = (Timer) metrics.computeIfAbsent(metricKey, x -> Kamon.timer(timerAnnotation.name()).withoutTags());

        if (containsELExpression(timerAnnotation.name(), timerAnnotation.tags())) {
            return (Timer) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
                final String name = getOperationName(timerAnnotation.name(), obj, clazz, className, methodName);
                final Map<String, Object> tags = getTags(obj, clazz, timerAnnotation.tags());
                if(tags.isEmpty()) return Kamon.timer(name).withoutTags();
                return Kamon.timer(name).withTags(TagSet.from(tags));
            }));
        }
        return timer;
    }


    public static SpanBuilder getSpanBuilder(Method method, Object obj, Class<?> clazz, String className, String methodName) {
        final MetricKey metricKey = MetricKey.from("Trace", method, clazz);
        final kamon.annotation.api.Trace traceAnnotation = method.getAnnotation(kamon.annotation.api.Trace.class);

        final SpanBuilder spanBuilder = (SpanBuilder) metrics.computeIfAbsent(metricKey, x -> Kamon.spanBuilder(traceAnnotation.operationName()));

        if (containsELExpression(traceAnnotation.operationName(), traceAnnotation.tags())) {
            return (SpanBuilder) metrics.computeIfPresent(metricKey, (key, c) -> key.cache.computeIfAbsent(obj, (x) -> {
                final String name = getOperationName(traceAnnotation.operationName(), obj, clazz, className, methodName);
                final Map<String, Object> tags = getTags(obj, clazz, traceAnnotation.tags());
                final SpanBuilder builder = Kamon.spanBuilder(name);
                tags.forEach((k, v)-> builder.tag(k, v.toString()));
                return builder;
            }));
        }
        return spanBuilder;
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

    private static ExpirationListener<MetricKey, Object> ExpirationListener() {
        return (key, value) ->   {
            if(value instanceof Instrument) ((Instrument) value).remove();
            Logger.debug(() -> "Expiring key: " + key + "with value" + value);
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

        public final Map<Object, Object> cache =  new HashMap<>();

        private  MetricKey(String prefix, Method method, Class<?> clazz) {
            this.prefix = prefix;
            this.method = method;
            this.clazz = clazz;
        }

        public static MetricKey from(String prefix, Method method, Class<?> clazz){
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
