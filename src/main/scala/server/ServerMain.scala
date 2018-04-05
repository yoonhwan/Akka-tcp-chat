package chatapp.server

import akka.actor.{ActorSystem, Props, PoisonPill, DeadLetter}
import scala.io
/**
  * Created by Niels Bokmans on 30-3-2016.
  */
object ServerMain extends App {
  import ClientHandlerMessages._
  val system = ActorSystem("ServerMain")
  val server = system.actorOf(Props(new ServerActor(system)), "server-actor")
  val bufferedReader = io.Source.stdin.bufferedReader()
  loop("")

  def loop(message: String): Boolean = message match {
    case "~quit" =>
      system stop server
      system.terminate()
      false
    case _ =>
      val msg = bufferedReader.readLine()
      server ! SendMessage("", msg, true)
      loop(msg)
  }

  val deadLettersSubscriber = system.actorOf(Props[EchoActor], name = "dead-letters-subscriber")
  system.eventStream.subscribe(deadLettersSubscriber, classOf[DeadLetter])
}
