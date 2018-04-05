package chatapp.client

import java.net.InetSocketAddress
import java.net.InetAddress
import akka.actor.{ActorSystem, Props, PoisonPill}
import akka.io.Tcp._
import chatapp.client.ClientMessage.SendMessage
import scala.io

/**
  * Created by Niels Bokmans on 30-3-2016.
  */
object ClientMain extends App {

    
  val system = ActorSystem("ClientMain")
  val Port:Int = system.settings.config.getInt("akka.server.port")
  val Server:String = system.settings.config.getString("akka.server.hostname")
  val clientConnection = system.actorOf(Props(new ClientActor(new InetSocketAddress(InetAddress.getByName(Server), Port), system)))
  val bufferedReader = io.Source.stdin.bufferedReader()
  loop("")

  def loop(message: String): Boolean = message match {
    case "~quit" =>
      clientConnection ! PoisonPill
      system.terminate()
      false
    case "~close" =>
      clientConnection ! PoisonPill
      system.terminate()
      false
    case _ =>
      val msg = bufferedReader.readLine()
      clientConnection ! SendMessage(msg)
      loop(msg)
  }
}
