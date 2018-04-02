
package chatapp.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import scala.concurrent.{Await,Future}
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

        
object ClientHandlerActor {

  def props(supervisor: ActorRef, connection: ActorRef, remote: InetSocketAddress): Props =
    Props(classOf[ClientHandlerActor], supervisor, connection, remote)

}

class ClientHandlerActor(supervisor: ActorRef, connection: ActorRef, remote: InetSocketAddress)
  extends Actor with ActorLogging {

    import Tcp._
    import ClientHandlerActor._
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    
    implicit val timeout = Timeout(5 seconds)
    val CommandCharacter = "~"
    var Identify = ""

    override def preStart(): Unit = {
      connection ! Register(self, keepOpenOnPeerClosed = true)
    }

    send("Please identify yourself using ~identify [name]!", serverMessage = true)
      
    // sign death pact: this actor terminates when connection breaks
    context watch connection

    case object Ack extends Event
    // start out in optimistic write-through mode
    def receive = writing

    def writing: Receive = {   
      case Received(data) =>
        log.info("Received : " + data)
        buffer(data)
        ProccessData(data)
        context.become({
          case Received(data) =>  buffer(data)
          case Ack            =>  acknowledge()
          case PeerClosed     =>  closing = true
          case _: ConnectionClosed => closing = true
        }, discardOld = false)
      case PeerClosed     => 
        log.info("PeerClosed")
        stopProc()
      case _: ConnectionClosed =>
          log.info("connection ConnectionClosed")
          stopProc()
      case obj: Terminated => 
        log.info(obj + " : Terminated")
      case Tcp.Aborted =>
        log.info("connection aborted")
      case Tcp.ConfirmedClosed =>
        log.info("connection ConfirmedClosed")
      case Tcp.Closed =>
        log.info("connection Closed")
      case _: Unbound =>
        log.info("connection Unbound")
    }

    override def postStop(): Unit = {
      super.postStop()
      log.info(s"transferred $transferred bytes from/to [$remote]")
    }

    def stopProc(): Unit = {
      supervisor ! DisconnectedClientHandlerActor(self.path.name)
      context stop self
    }

    var storage = Vector.empty[ByteString]
    var stored = 0L
    var transferred = 0L
    var closing = false

    val maxStored = 100000000L
    val highWatermark = maxStored * 5 / 10
    val lowWatermark = maxStored * 3 / 10
    var suspended = false

    private def buffer(data: ByteString): Unit = {
      storage :+= data
      stored += data.size

      if (stored > maxStored) {
        log.warning(s"drop connection to [$remote] (buffer overrun)")
        stopProc()

      } else if (stored > highWatermark) {
        log.debug(s"suspending reading")
        connection ! SuspendReading
        suspended = true
      }
    }

    private def acknowledge(): Unit = {
      require(storage.nonEmpty, "storage was empty")

      val size = storage(0).size
      stored -= size
      transferred += size

      storage = storage drop 1

      if (suspended && stored < lowWatermark) {
        log.debug("resuming reading")
        connection ! ResumeReading
        suspended = false
      }

      if (storage.isEmpty) {
        log.info("acknowledge buffer is cleaned!")
        if (closing) stopProc()
        else context.unbecome()
      } else {
        log.info("acknowledge buffer has delayed data! continue sending data")
        ProccessData(storage(0))
      }
    }

    def ProccessData(data: ByteString): Unit = {
      val text = data.decodeString("US-ASCII")
      val clientActorName = self.path.name
      if (isCommand(text)) {
        getCommand(text) match {
          case "quit" => //quit(clientActorName)
          case "identify" => identify(clientActorName, text)
          case "online" => online(clientActorName)
          case _ => send("Unknown command!", serverMessage = true)
        }
      } 
      else {
        if (Identify.length <= 0) {
          send("Please identify yourself using ~identify [name]!", serverMessage = true)
        } else {
          send("from <"+Identify+"> user message : " + text , serverMessage = true)
          // sendToAll(ClientIdentities.get(clientActorName).get, text)
        }
      }
    }

    def isCommand(message: String): Boolean = {
      message.startsWith(CommandCharacter)
    }

    def getCommand(message: String): String = {
      val split = message.split(" ")
      val command = split(0)
      val actualCommand = command.substring(1, command.length())
      actualCommand
    }

    def identify(clientActorName: String, text: String) = {
      val split = text.split(" ")
      if (split.length == 1) {
        send("Please enter what username you would like to identify with!", serverMessage = true)
      } else {
        val clientActorName = self.path.name
        val desiredName = split(1)

        val future = supervisor ? HasIdentifier(clientActorName, desiredName)
        val result = Await.result(future, 1 seconds).asInstanceOf[String]
        if(result.length > 0){
          send("There is already an user with this username! [" + result + "]", serverMessage = true)
        }else {

          val future_sub = supervisor ? SetIdentifier(clientActorName, desiredName)
          val result_sub = Await.result(future_sub, 1 seconds).asInstanceOf[String]
          Identify = result_sub
          send("Successfully set your identity to " + Identify, serverMessage = true)
        }
        
        // if (ClientIdentities.values.exists(_ == desiredName)) {
        //   
        // } else {
          // ClientIdentities += (clientActorName -> desiredName)
          
        // }
      }
    }

    def online(clientActorName: String): Unit = {
      val future = supervisor ? GetAllClintIdentifier
      val result = Await.result(future, 1 seconds).asInstanceOf[String]
      if(result.length > 0){
        send("Currently active users: " + result, serverMessage = true)
      }
    }
    def send(message: String, serverMessage: Boolean = false) = {
      if (serverMessage) {
        connection ! Write(ByteString("[SERVER]: " + message), Ack)
      } else {
        connection ! Write(ByteString(message), Ack)
      }
    }


  }