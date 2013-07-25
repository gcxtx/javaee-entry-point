/**
 * (c) Copyright 2013, Tata Consultancy Services & Ecole Polytechnique de Montreal
 * All rights reserved
 */

package soot.jimple.toolkits.javaee.detectors

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.JavaConverters._
import soot.jimple.toolkits.javaee.model.servlet.Web
import soot.{Unit => SootUnit, _}
import soot.jimple.toolkits.javaee.model.servlet.jboss.JBossWSTestServlet
import soot.util.ScalaWrappers._

object JBossWSTestDetector {
  final val GENERATED_CLASS_NAME = "JBossWSTestServlet"
}

import JBossWSTestDetector._

class JBossWSTestDetector extends AbstractServletDetector with Logging{

  override def detectFromSource(web: Web) {

    val jBossWsSuperClass = Scene.v.forceResolve("org.jboss.wsf.test.JBossWSTest", SootClass.HIERARCHY) //otherwise we can't load the type
    val jBossWsSuperType = jBossWsSuperClass.getType

    val nonDandling : Seq[SootClass] = Scene.v.applicationClasses.par.filter(_.resolvingLevel() > SootClass.DANGLING).seq.toSeq
    logger.trace("Non-dandling classes ({}): {}", nonDandling.size : Integer, nonDandling.map(_.name))

    val fastHierarchy = Scene.v.getOrMakeFastHierarchy //make sure it is created before the parallel computations steps in
    val jBossWsClients : Seq[SootClass]= nonDandling.par.filter(_.isConcrete).
      filter(sc=>fastHierarchy.canStoreType(sc.getType,jBossWsSuperType)).seq
    jBossWsClients.foreach(logger.info("Found JBoss WS Test Client: {}", _))

    val testMethods : Seq[SootMethod]= jBossWsClients.par.flatMap(_.methods).filter(_.name.startsWith("test")).seq
    testMethods.foreach(logger.debug("Test method found: {}", _))

    if (!testMethods.isEmpty){
      val fakeServlet = new JBossWSTestServlet(jBossWsClients.asJava, testMethods.asJava)

      val fullName = web.getGeneratorInfos.getRootPackage + "." + GENERATED_CLASS_NAME
      fakeServlet.setClazz(fullName)
      fakeServlet.setName(GENERATED_CLASS_NAME)
      web.getServlets.add(fakeServlet)
      web.bindServlet(fakeServlet, "/jbosstester")
    }
  }

  override def detectFromConfig(web: Web) {
    detectFromSource(web) //No config available for that case
  }

  // ----------------------- Template part of the interface
  override def getModelExtensions: java.util.List[Class[_]] = List[Class[_]]().asJava

  override def getCheckFiles: java.util.List[String] = return List[String]().asJava

  override def getTemplateFiles: java.util.List[String] =
    List[String]("soot::jimple::toolkits::javaee::templates::jboss::JBossTestWSWrapper::main",
      "soot::jimple::toolkits::javaee::templates::ws::JaxWsServiceWrapper::main").asJava
}
