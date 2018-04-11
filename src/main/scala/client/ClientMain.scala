package chatapp.client

import java.net.InetSocketAddress
import java.net.InetAddress
import akka.actor.{ActorSystem, Props, PoisonPill}
import akka.io.Tcp._
import chatapp.client.ClientMessage.SendMessage
import scala.io
import scala.util.control.Breaks._

/**
  * Created by yoonhwan on 02-4-2018
  */
object ClientMain extends App {

    
  val system = ActorSystem("ClientMain")
  val Port:Int = system.settings.config.getInt("akka.server.port")
  val Server:String = system.settings.config.getString("akka.server.hostname")
  val clientConnection = system.actorOf(Props(new ClientActor(new InetSocketAddress(InetAddress.getByName(Server), Port), system, null)))
  val bufferedReader = io.Source.stdin.bufferedReader()
  
  var line: String = null
  while ({line = bufferedReader.readLine; line != null}) { 
      if(loop(line)==false)
        break
  }

  def loop(message: String): Boolean = message match {
    case "~quit" =>
      clientConnection ! PoisonPill
      system.terminate()
      false
    case _ =>
      clientConnection ! SendMessage(message)
      true

  }
}
