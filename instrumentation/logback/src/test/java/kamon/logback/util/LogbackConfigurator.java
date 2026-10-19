/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.logback.util;


import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.spi.PropertyContainer;

import java.util.HashMap;
import java.util.Map;


/**
 * Allows programmatic configuration of logback which is usually faster than parsing XML, see [1] for the original source.
 *
 * [1] https://github.com/spring-projects/spring-boot/blob/master/spring-boot/src/main/java/org/springframework/boot/logging/logback/LogbackConfigurator.java
 */
public class LogbackConfigurator {

    private LoggerContext context;

    public LogbackConfigurator(LoggerContext context) {
        this.context = context;
    }

    public PropertyContainer getContext() {
        return this.context;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void conversionRule(String conversionWord, Class<? extends Converter> converterClass) {
        Map<String, String> registry = (Map<String, String>) this.context
                .getObject(CoreConstants.PATTERN_RULE_REGISTRY);
        if (registry == null) {
            registry = new HashMap<>();
            this.context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, registry);
        }
        registry.put(conversionWord, converterClass.getName());
    }

    public void appender(String name, Appender<?> appender) {
        appender.setName(name);
        start(appender);
    }

    public void start(LifeCycle lifeCycle) {
        if (lifeCycle instanceof ContextAware) {
            ((ContextAware) lifeCycle).setContext(this.context);
        }
        lifeCycle.start();
    }
}