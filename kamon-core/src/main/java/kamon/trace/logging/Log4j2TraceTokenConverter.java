/* ===================================================
 * Copyright © 2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.trace.logging;

import kamon.trace.TraceContext;
import kamon.trace.Tracer;
import kamon.util.Function;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.*;
import scala.runtime.AbstractFunction0;

@Plugin(name = "TraceTokenConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"traceToken"})
public class Log4j2TraceTokenConverter extends LogEventPatternConverter {

    protected Log4j2TraceTokenConverter(String name, String style) {
        super(name, style);
    }

    public static Log4j2TraceTokenConverter newInstance(String[] options) {
        return new Log4j2TraceTokenConverter("traceToken", "traceToken");
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        toAppendTo.append(traceToken());
    }

    protected String traceToken() {
        return Tracer.currentContext().collect(new Function<TraceContext, String>() {
            @Override
            public String apply(TraceContext t) {
                return t.token();
            }
        }).getOrElse(new AbstractFunction0<String>() {
            @Override
            public String apply() {
                return "undefined";
            }
        });
    }
}