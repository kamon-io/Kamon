package kamon.instrumentation.apache.httpclient.util

import org.apache.http.client.ResponseHandler
import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

class StringResponseHandler extends ResponseHandler[String] {

  def handleResponse(response: HttpResponse): String = {
    val code = response.getStatusLine().getStatusCode()
    if (200 until 300 contains code) {
      return EntityUtils.toString(response.getEntity())
    }
    return null
  }

}

class ErrorThrowingHandler extends ResponseHandler[String] {

  def handleResponse(response: HttpResponse): String = {
    throw new RuntimeException("Dummy Exception")
  }

}
