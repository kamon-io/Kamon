package kamon.annotation.el

import javax.el.ELProcessor

import kamon.annotation.el.resolver.PrivateFieldELResolver

/**
 * Created by diego on 2/21/15.
 */
object ELProcessorFactory {
  def withClass(clazz: Class[_]): ELProcessor = {
    val processor = create()
    processor.getELManager.importClass(clazz.getName)
    processor
  }

  def withObject(obj: AnyRef): ELProcessor = {
    val processor = create()
    processor.getELManager.importClass(obj.getClass.getName)
    processor.defineBean("this", obj)
    processor
  }

  private def create(): ELProcessor = {
    val processor = new ELProcessor()
    processor.getELManager.addELResolver(new PrivateFieldELResolver())
    processor
  }
}
