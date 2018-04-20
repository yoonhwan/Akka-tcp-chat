package chatapp.client

import java.net.InetSocketAddress
import java.nio.ByteOrder

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Terminated}
import akka.io.Tcp._
import akka.io._
import akka.util.{ByteString, CompactByteString}
import chatapp.client.ClientMessage.SendMessage
import chatapp.server.Buffering

/**
  * Created by yoonhwan on 02-4-2018
  */

class ClientActor(address: InetSocketAddress, actorSystem: ActorSystem, stresstestActor: ActorRef) 
extends Actor with ActorLogging with Buffering{
  IO(Tcp)(actorSystem) ! Connect(address)

  implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
  
  def receive: Receive = {
    case CommandFailed(command: Tcp.Command) =>
      log.info("Failed to connect to " + address.toString + " : " + command.toString)
//      self ! Kill
      if(stresstestActor!=null){
        stresstestActor ! chatapp.client.ClientMessage.ClientError("Failed to connect to " + address.toString + " : " + command.toString)
      }else{
        actorSystem.terminate()
      }
          
    case Connected(remote, local) =>
      log.info("Successfully connected to " + address)
      val connection = sender()
      context watch connection
      connection ! Register(self, true, true)
      context become buffer(connection, CompactByteString())

      if(stresstestActor!=null){
        stresstestActor ! chatapp.client.ClientMessage.ClientConnected
      }
  }

  def buffer(connection:ActorRef, buf:ByteString): Receive = {
    case Received(data) =>
      val msg = buf ++ data
      val (pkt, remainder) = getPacket(msg)
      // Do something with your packet
    
      pkt.foreach(f=>{
        if(stresstestActor==null)
          log.info(f.utf8String)
        if(stresstestActor!=null && (f.utf8String contains "[ERROR]") == true){
          stresstestActor ! chatapp.client.ClientMessage.ClientError(s"receive error message kill actor : ${f.utf8String}")
          log.info(s"receive error message kill actor : ${f.utf8String}" )
        }
      }  
      )

      context become buffer(connection, remainder) 
    case SendMessage(message) =>
      val msg = ByteString(message,"UTF-8")
      val packet = ByteString.newBuilder
                              .putInt(msg.length)
                              .result() ++ msg
      connection ! Write(packet)

      if(sender != context.system.deadLetters)
      {
        sender match {
          case null =>
          case _ => sender ! "success"
        }
      }
      
      // log.info(s"send message :${packet.length}")

    case PeerClosed     => 
      log.info("PeerClosed")
    case _: ConnectionClosed =>
      context stop self
      // actorSystem.terminate()
      log.info("connection closed")
      
    case Terminated(obj) =>
      log.info(obj + " : Terminated")
  }
  
}

class ClientActorUDP(address: InetSocketAddress, actorSystem: ActorSystem, stresstestActor: ActorRef)
  extends Actor with ActorLogging with Buffering{
  import context.system
  IO(UdpConnected) ! UdpConnected.Connect(self, address)

  def receive: Receive = {
    case UdpConnected.Connected ⇒
      log.info("Successfully connected to " + address)
      context.become(ready(sender()))
  }

  def ready(connection: ActorRef): Receive = {
    case UdpConnected.Received(data) ⇒
    // process data, send it on, etc.
      log.info(s"message received : ${data}")
    case msg: String ⇒
      connection ! UdpConnected.Send(ByteString(msg))
    case UdpConnected.Disconnect ⇒
      connection ! UdpConnected.Disconnect
    case UdpConnected.Disconnected ⇒ context.stop(self)
    case SendMessage(msg) =>
      connection ! UdpConnected.Send(ByteString(msg))
  }
}