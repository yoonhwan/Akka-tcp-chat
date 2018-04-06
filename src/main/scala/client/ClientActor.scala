package chatapp.client

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Kill, Terminated,ActorLogging}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import java.nio.ByteOrder
import akka.util.{ByteString,CompactByteString}
import chatapp.client.ClientMessage.SendMessage
import scala.util.{Success,Failure}
import chatapp.server.Buffering

/**
  * Created by Niels Bokmans on 30-3-2016.
  */
class ClientActor(address: InetSocketAddress, actorSystem: ActorSystem) 
extends Actor with ActorLogging with Buffering{

  IO(Tcp)(actorSystem) ! Connect(address)

  implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
  

  def receive: Receive = {
    case CommandFailed(command: Tcp.Command) =>
      log.info("Failed to connect to " + address.toString)
      self ! Kill
      actorSystem.terminate()
    case Connected(remote, local) =>
      log.info("Successfully connected to " + address)
      val connection = sender()
      context watch connection
      connection ! Register(self)
      context become buffer(connection, CompactByteString())
      
  }
  def buffer(connection:ActorRef, buf:ByteString): Receive = {
    case Received(data) =>
      val msg = buf ++ ByteString(data.utf8String,"UTF-8")
      val (pkt, remainder) = getPacket(msg)
        // Do something with your packet
      pkt.foreach(f=>log.info(f.utf8String))
      context become buffer(connection, remainder) 
    case SendMessage(message) =>
      val msg = ByteString(message,"UTF-8")
      connection ! Write(ByteString.newBuilder
                .putInt(msg.length.toInt)
                .result() ++ msg)

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
