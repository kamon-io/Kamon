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

package kamon.instrumentation.elasticsearch

import kamon.Kamon
import kamon.trace.Span
import kanela.agent.api.instrumentation.InstrumentationBuilder
import org.apache.http.HttpEntity
import org.elasticsearch.client.{Response, ResponseListener}

class ESInstrumentation extends InstrumentationBuilder {
  onType("org.elasticsearch.client.RestClient")
    .advise(method("performRequestAsync").and(takesArguments(2)), classOf[AsyncElasticsearchRestClientInstrumentation])
    .advise(method("performRequest").and(takesArguments(1)), classOf[SyncElasticsearchRestClientInstrumentation])

  onType("org.elasticsearch.client.RestHighLevelClient")
    .advise(
      method("internalPerformRequest").and(takesArguments(5)),
      classOf[HighLevelElasticsearchClientInstrumentation]
    )
    .advise(
      method("internalPerformRequestAsync").and(takesArguments(6)),
      classOf[HighLevelElasticsearchClientInstrumentation]
    )
}

class InstrumentedListener(inner: ResponseListener, span: Span) extends ResponseListener {
  override def onSuccess(response: Response): Unit = {
    span.finish()
    inner.onSuccess(response)
  }

  override def onFailure(exception: Exception): Unit = {
    span.fail(exception)
    inner.onFailure(exception)
  }
}

object RequestSizeHistogram {
  private val histogram = Kamon.histogram("elastic.request.size").withoutTags()

  def record(entity: HttpEntity): Unit = {
    Option(entity)
      .map(_.getContentLength)
      .filter(_ >= 0)
      .foreach(histogram.record)
  }
}
