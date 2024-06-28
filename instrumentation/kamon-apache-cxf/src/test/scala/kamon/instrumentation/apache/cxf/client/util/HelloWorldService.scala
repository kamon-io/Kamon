package kamon.instrumentation.apache.cxf.client.util

import javax.jws.{WebMethod, WebService}

@WebService
trait HelloWorldService {
  @WebMethod
  def sayHello(): String
}
