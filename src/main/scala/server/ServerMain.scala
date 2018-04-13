package chatapp.server

import akka.actor.{ActorSystem, Props, PoisonPill, DeadLetter}
import scala.io
import scala.util.control.Breaks._
import kamon.util.DynamicAccess
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import kamon.zipkin.ZipkinReporter
import kamon.jaeger.JaegerReporter
import kamon.kamino.{KaminoReporter, KaminoTracingReporter}
/**
  * Created by yoonhwan on 02-4-2018
  */
object ServerMain extends App {
  import ClientHandlerMessages._
  import ClientHandlerSupervisor._

//  Kamon.loadReportersFromConfig()
//  Kamon.addReporter(new PrometheusReporter())
//  Kamon.addReporter(new ZipkinReporter())
  // Kamon.addReporter(new JaegerReporter())
  Kamon.addReporter(new KaminoReporter())
  Kamon.addReporter(new KaminoTracingReporter())

  val system = ActorSystem("ServerMain")
  val server = system.actorOf(Props(new ServerActor(system)), "server-actor")
  val bufferedReader = io.Source.stdin.bufferedReader()
  

  var line: String = null
  while ({line = bufferedReader.readLine; line != null}) { 
      if(loop(line)==false)
        break
  }

  def loop(message: String): Boolean = message match {
    case ":quit" =>
      system stop server
      system.terminate()
      false
    case ":clearroom" =>
      server ! ClearAllChatRoom
      true
    case _ =>
      server ! SendServerMessage(message)
      true
      
      
  }

  val deadLettersSubscriber = system.actorOf(Props[EchoActor], name = "dead-letters-subscriber")
  system.eventStream.subscribe(deadLettersSubscriber, classOf[DeadLetter])
}
