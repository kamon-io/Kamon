/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon
package trace

import kamon.context.Context

/**
  * Built-in hooks that can be configured on the "kamon.tracer.hooks" setting.
  */
object Hooks {

  object PreStart {

    /**
      * Creates a PreStartHook that updates the operation name to the provided one. This builder becomes useful when
      * use together with the "PreStart.FromContext" hook.
      */
    def updateOperationName(operationName: String): Tracer.PreStartHook = new Tracer.PreStartHook {
      override def beforeStart(builder: SpanBuilder): Unit = builder.name(operationName)
    }

    /** Context key on used to store and retrieve PreStartTransformation instances on/from the current Context. */
    val Key = Context.key[Tracer.PreStartHook]("preStartTransformation", Noop)

    /**
      * Tries to find a PreStartHook instance on the current Context and apply it. Since the default value for the
      * Context key is the Noop implementation, no changes will be applied if no PreStartHook is found on the current
      * Context.
      */
    class FromContext extends Tracer.PreStartHook {
      override def beforeStart(builder: SpanBuilder): Unit =
        Kamon.currentContext().get(PreStart.Key).beforeStart(builder)
    }

    /** PreStartTransformation implementation which does not apply any changes to the provided SpanBuilder. */
    object Noop extends Tracer.PreStartHook {
      override def beforeStart(builder: SpanBuilder): Unit = {}
    }

  }

  object PreFinish {

    /**
      * Creates a PreFinishHook that updates the operation name to the provided one. This builder becomes useful when
      * use together with the "PreFinish.FromContext" hook.
      */
    def updateOperationName(operationName: String): Tracer.PreFinishHook = new Tracer.PreFinishHook {
      override def beforeFinish(span: Span): Unit = span.name(operationName)
    }

    /** Context key on used to store and retrieve PreFinishTransformation instances on/from the current Context. */
    val Key = Context.key[Tracer.PreFinishHook]("preFinishTransformation", Noop)

    /**
      * Tries to find a PreFinishHook instance on the current Context and apply it. Since the default value for the
      * Context key is the Noop implementation, no changes will be applied if no PreFinishHook is found on the current
      * Context.
      */
    class FromContext extends Tracer.PreFinishHook {
      override def beforeFinish(span: Span): Unit =
        Kamon.currentContext().get(PreFinish.Key).beforeFinish(span)
    }

    /** PreFinishTransformation implementation which does not apply any changes to the provided Span. */
    object Noop extends Tracer.PreFinishHook {
      override def beforeFinish(span: Span): Unit = {}
    }
  }
}
