package chatapp.server

import akka.actor.{ActorSystem, Props, PoisonPill, DeadLetter}
import scala.io
import scala.util.control.Breaks._

/**
  * Created by Niels Bokmans on 30-3-2016.
  */
object ServerMain extends App {
  import ClientHandlerMessages._
  import ClientHandlerSupervisor._
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
