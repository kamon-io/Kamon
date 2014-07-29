/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.macros

import scala.language.experimental.macros
import scala.reflect.macros.Context

object InlineTraceContextMacro {

  // Macro to generate an inline version of kamon.trace.TraceRecorder.withTraceContext
  def withInlineTraceContextImpl[T: c.WeakTypeTag, TC: c.WeakTypeTag](c: Context)(traceCtx: c.Expr[TC])(thunk: c.Expr[T]) = {
    import c.universe._

    val inlineThunk =
      Block(
        List(
          ValDef(
            Modifiers(), newTermName("oldContext"), TypeTree(),
            Select(Ident(newTermName("TraceRecorder")), newTermName("currentContext"))),
          Apply(
            Select(Ident(newTermName("TraceRecorder")), newTermName("setContext")),
            List(traceCtx.tree))),
        Try(
          thunk.tree,
          List(),
          Apply(
            Select(Ident(newTermName("TraceRecorder")), newTermName("setContext")),
            List(Ident(newTermName("oldContext"))))))

    c.Expr[T](inlineThunk)
  }
}
