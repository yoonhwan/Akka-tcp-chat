package chatapp.client

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorSystem, Kill, Terminated,ActorLogging}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import chatapp.client.ClientMessage.SendMessage

/**
  * Created by Niels Bokmans on 30-3-2016.
  */
class ClientActor(address: InetSocketAddress, actorSystem: ActorSystem) extends Actor with ActorLogging{

  IO(Tcp)(actorSystem) ! Connect(address)

  def receive = {
    case CommandFailed(command: Tcp.Command) =>
      log.info("Failed to connect to " + address.toString)
      self ! Kill
      actorSystem.terminate()
    case Connected(remote, local) =>
      log.info("Successfully connected to " + address)
      val connection = sender()
      context watch sender
      connection ! Register(self)
      context become {
        case Received(data) =>
          log.info(data.decodeString("UTF-8"))
        case SendMessage(message) =>
          connection ! Write(ByteString(message))
        case PeerClosed     => 
          log.info("PeerClosed")
        case _: ConnectionClosed =>
          context stop self
          actorSystem.terminate()
          log.info("connection closed")
        case Terminated(obj) =>
          log.info(obj + " : Terminated")
      }
  }
}
