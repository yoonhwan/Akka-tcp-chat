
package chatapp.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import java.nio.ByteOrder
import akka.util.{ByteString,CompactByteString}
import scala.concurrent.{Await,Future}
import scala.util.{Success,Failure}
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.pipe
        
object ClientHandlerActor {

  def props(supervisor: ActorRef, connection: ActorRef, remote: InetSocketAddress): Props =
    Props(classOf[ClientHandlerActor], supervisor, connection, remote)

  case object GetClientInfomation
  case class ClientInfomation(userIdentify: String, remote: InetSocketAddress, roomName: String)

}

class ClientHandlerActor(supervisor: ActorRef, connection: ActorRef, remote: InetSocketAddress)
  extends Actor with ActorLogging with Buffering{

    import Tcp._
    import ClientHandlerActor._
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    
    implicit val timeout = Timeout(5 seconds)
    val CommandCharacter = ":"
    var userIdentify = ""
    var roomName = ""

    override def preStart(): Unit = {
      connection ! Register(self, keepOpenOnPeerClosed = true)
    }

    send("Please userIdentify yourself using ~identify [name]!", serverMessage = true)
    // sign death pact: this actor terminates when connection breaks
    context watch connection
    
    // start out in optimistic write-through mode
    def receive = common orElse writing(CompactByteString())

    def common: Receive = {
      case PeerClosed     => 
        log.info("PeerClosed")
        stopProc()
      case _: ConnectionClosed =>
        log.info("connection ConnectionClosed")
        stopProc()
      case Terminated(obj) => 
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
    
    def writing(buf: ByteString): Receive = common orElse {   
      case SendMessage(clientActorName, message, serverMessage) =>
        if(serverMessage)
          send(message, serverMessage)
        else if(clientActorName != userIdentify)
          send("from <"+clientActorName+"> user message : " + message, serverMessage)

      case Received(data) =>
        val msg = buf ++ ByteString(data.utf8String,"UTF-8")
        val (pkt, remainder) = getPacket(msg)
        // Do something with your packet
        pkt.foreach(f=>ProccessData(f))
        context become writing(remainder) 

      case GetClientInfomation =>
        sender() ! ClientInfomation(userIdentify, remote, roomName)

      case DynamicGroupRouter.DestroyGroupRouter =>
        roomName = ""
    }

    override def postStop(): Unit = {
      super.postStop()
      log.info(s"stoped actor user : $userIdentify from/to [$remote]")
    }

    def stopProc(): Unit = {
      supervisor ! DisconnectedClientHandlerActor(self)
    }

    
    def ProccessData(data: ByteString): Unit = {
      
      val text = data.utf8String
      val clientActorName = self.path.name
      if (isCommand(text)) {
        getCommand(text) match {
          case "quit" => //quit(clientActorName)
          case "identify" => identify(clientActorName, text)
          case "online" => online(clientActorName)
          case "chatroom" => chatroom()
          case "create" => createChatRoom(self, text)
          case "join" => joinChatRoom(self, text)
          case "exit" => exitChatRoom(self)
          case _ => send("Unknown command!", serverMessage = true)
        }
      } 
      else {
        if (userIdentify.length <= 0) {
          send("Please userIdentify yourself using ~identify [name]!", serverMessage = true)
        } else {
          supervisor ! SendMessage(userIdentify, text, false)
          // send("from <"+userIdentify+"> user message : " + text , serverMessage = true)
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

        val future: Future[Any] = supervisor ? HasIdentifier(clientActorName, desiredName)
        future onComplete {
          case Success(result) => {
            val future_sub: Future[Any] = supervisor ? SetIdentifier(clientActorName, desiredName)
            future_sub onComplete {
              case Success(result_sub) => {
                userIdentify = result_sub.asInstanceOf[String]
                send("Successfully set your identity to " + userIdentify, serverMessage = true)
              }
              case Failure(t) => t.printStackTrace
            }
          }
          case Failure(t) => send("There is already an user with this username! [" + desiredName + "]", serverMessage = true)
        }
      }
    }

    def online(clientActorName: String): Unit = {
      supervisor ? GetAllClientIdentifier() onComplete {
        case Success(result) => {
          val message = result.asInstanceOf[String]
          if(message.length > 0){
            send("Currently active users: " + message, serverMessage = true)
          }
        }
        case Failure(t) => t.printStackTrace
      }
    }

    def chatroom():Unit = {
      supervisor ? GetAllChatRoomInfo(self) onComplete {
        case Success(result) => {
          val message = result.asInstanceOf[String]
          if(message.length > 0){
            send("Currently active chatting rooms: " + message, serverMessage = true)
          }
        }
        case Failure(t) => 
          log.info(t.getMessage + "::::" + t.getStackTrace)
          t.printStackTrace
      }
    }

    def createChatRoom(actor:ActorRef, text:String):Unit = {
      if(roomName.length > 0) {
        send(s"Already joined chat room : $roomName, if you need create room. exit room first.", serverMessage = true)
      }else {
        val split = text.split(" ")
        if (split.length == 1) {
            send("Please enter what chatroom name you would like to create room with!", serverMessage = true)
        } else{
          val name = split(1)
          supervisor ? MakeChatRoom(actor, name) onComplete {
            case Success(result) => {
              roomName = result.asInstanceOf[String]
              if(roomName.length > 0){
                send(s"Successfully create the chatroom($roomName).", serverMessage = true)
              }
            }
            case Failure(t) => send("There is already an room with this roomname! [" + name + "]", serverMessage = true)
          }
        }
      }
    }

    def joinChatRoom(actor:ActorRef, text:String):Unit = {
      val split = text.split(" ")
      if (split.length == 1) {
        send("Please enter what chatroom name you would like to joinning with!", serverMessage = true)
      } else{
        val name = split(1)
        supervisor ? JoinChatRoom(actor, name) onComplete {
          case Success(result) => {
            roomName = result.asInstanceOf[String]
            if(roomName.length > 0){
              send(s"Successfully join the chatroom($roomName).", serverMessage = true)
            }
          }
          case Failure(t) => send("There is not exist an room name! [" + name + "]", serverMessage = true)
        }
      }
    }

    def exitChatRoom(actor:ActorRef):Unit = {

      if(roomName.length <= 0) {
        send(s"Does not joined any room. doesn't joined any rooms", serverMessage = true)
      }else {
        supervisor ? ExitChatRoom(actor, roomName) onComplete {
          case Success(result) => {
            send(s"Successfully exit the chatroom($roomName).", serverMessage = true)
            roomName = ""
          }
          case Failure(t) => t.printStackTrace
        }
      }
    }

    def send(message: String, serverMessage: Boolean = false) = {
      if (serverMessage) {
        context.actorSelection(connection.path) ? Write(makePacket("[SERVER]: " + message))
      } else {
        context.actorSelection(connection.path) ? Write(makePacket(message))
      }
    }

    def makePacket(message: String): ByteString = {
      implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
      val msg = ByteString(message,"UTF-8")
      ByteString.newBuilder
                .putInt(msg.length.toInt)
                .result() ++ msg
    }
  }