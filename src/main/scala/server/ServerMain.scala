package chatapp.server

import akka.actor.{ActorSystem, Props, PoisonPill}
import scala.io
/**
  * Created by Niels Bokmans on 30-3-2016.
  */
object ServerMain extends App {

  val system = ActorSystem("ServerMain")
  val server = system.actorOf(Props(new ServerActor(system)))
  val bufferedReader = io.Source.stdin.bufferedReader()
  loop("")

  def loop(message: String): Boolean = message match {
    case "~quit" =>
      server ! PoisonPill
      system.terminate()
      false
    case _ =>
      val msg = bufferedReader.readLine()
    //   clientConnection ! SendMessage(msg)
      loop(msg)
  }
}
