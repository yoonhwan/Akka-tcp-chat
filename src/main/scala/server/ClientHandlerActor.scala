
package chatapp.server

import java.net.InetSocketAddress
import java.nio.ByteOrder

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.io.Tcp
import akka.pattern.ask
import akka.util.{ByteString, CompactByteString, Timeout}
import chatapp.client.SERIALIZER

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
        
object ClientHandlerActor {

  def props(supervisor: ActorRef, connection: ActorRef, remote: InetSocketAddress): Props =
    Props(classOf[ClientHandlerActor], supervisor, connection, remote)

  case object GetClientInfomation
  case class ClientInfomation(userIdentify: String, remote: InetSocketAddress, roomName: String)

}

class ClientHandlerActor(supervisor: ActorRef, connection: ActorRef, remote: InetSocketAddress)
  extends Actor with ActorLogging with Buffering{

    import ClientHandlerActor._
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    import Tcp._
    
    implicit val timeout = Timeout(5 seconds)
    val CommandCharacter = '|'
    var userIdentify = ""
    var roomName = ""
    var support_chat_all_users:Boolean = false
    var _typeOfSerializer = SERIALIZER.ROW

  override def preStart(): Unit = {
      connection ! Register(self)//, keepOpenOnPeerClosed = true)
      context watch connection
      // sign death pact: this actor terminates when connection breaks
      send("Please userIdentify yourself using <identify|[name]>", serverMessage = true)
    }

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
      case SendServerMessage(message) => {
        send(message, true)
      }
      case SendAllClientMessage(clientActorName, message) => {
        if(clientActorName != userIdentify)
          send(s"from <$clientActorName> user message : $message", false)
      }
      case SendRoomClientMessage(roomName, clientActorName, message) => {
        if(roomName.length <= 0)
          send("Please create or join room yourself using <create or join|[name]> Also you can show all list rooms <chatroom>", true)
        else
//          if(clientActorName != userIdentify)
//            send(s"from <$clientActorName> user message at room <$roomName> : $message", false)
          send(s"$message", false)
      }

      case Received(data) =>
        val msg = buf ++ data
        val (pkt, remainder) = getPacket(msg)
        // Do something with your packet
        pkt.foreach(f=>ProccessData(ReceiveData(f)))
//        log.info(s"recv message : ${data.utf8String.length} : dc = ${pkt.length} : rc = ${remainder.length} : data = ${data.utf8String}")
        context become writing(remainder) 

      case GetClientInfomation =>
        sender() ! ClientInfomation(userIdentify, remote, roomName)

      case DynamicGroupRouter.DestroyGroupRouter =>
        roomName = ""

      case SendErrorMessage(error) =>
        send(s"[ERROR] ${error}", false)
    }

    override def postStop(): Unit = {
      super.postStop()
      log.info(s"stoped actor user : $userIdentify from/to [$remote]")
    }

    def stopProc(): Unit = {
      if(roomName.length>0) {
        exitChatRoom(self)
      }

      supervisor ! DisconnectedClientHandlerActor(self)
    }

    
    def ProccessData(data: ByteString): Unit = {
//      log.info(s"ProccessData 1 :${data}")

      var text = data.utf8String
      val clientActorName = self.path.name
      val splitdata = getCommand(text)

      splitdata._1 match {
        case "quit" => //quit(clientActorName)
        case "identify" => checkCommandExcute(clientActorName, splitdata._2, identify _)
        case "online" => online(clientActorName)
        case "chatroom" => chatroom()
        case "create" => checkCommandExcute(self, splitdata._2, createChatRoom _)
        case "join" => checkCommandExcute(self, splitdata._2, joinChatRoom _)
        case "exit" => exitChatRoom(self)
        case "chat" => chat(splitdata._2)
        case _ => send("Unknown command! if you need show all command. type the command <help>.", serverMessage = true)
      }
    }

    def checkCommandExcute(actorName:String, msg:String, f:(String, String) => Unit): Unit =
    {
      if(msg.length() <= 0)
        send("Input Second value command! if you need show all command. type the command <help>.", serverMessage = true)
      else
        f(actorName, msg)
    }
    def checkCommandExcute(actor:ActorRef, msg:String, f:(ActorRef, String) => Unit): Unit =
    {
      if(msg.length() <= 0)
        send("Input Second value command! if you need show all command. type the command <help>.", serverMessage = true)
      else
        f(actor, msg)
    }

    def getCommand(message: String): (String , String)= {

      if(message contains "|") {
        val split = message.split(CommandCharacter)
//        split.foreach(log.info)
        (split(0), split(1))
      }else {
        (message, "")
      }
    }

    def chat(message: String) = {
      if (userIdentify.length <= 0) {
        send("Please userIdentify yourself using <identify|[name]>", serverMessage = true)
      } else {
        if(support_chat_all_users)
        {
          if(roomName.length <= 0)
            supervisor ! SendAllClientMessage(userIdentify, message)
          else
            supervisor ! SendRoomClientMessage(roomName, userIdentify, message)
        }else {
          if(roomName.length <= 0)
            send("Please create or join room yourself using <create or join|[name]> Also you can show all list rooms <chatroom>", serverMessage = true)
          else
            supervisor ! SendRoomClientMessage(roomName, userIdentify, message)
        }
      }
    }

    def identify(clientActorName: String, text: String) = {
      val clientActorName = self.path.name
      val desiredName = text

      val future: Future[Any] = supervisor ? HasIdentifier(clientActorName, desiredName)
      future onComplete {
        case Success(result) => {
          val future_sub: Future[Any] = supervisor ? SetIdentifier(clientActorName, desiredName)
          future_sub onComplete {
            case Success(result_sub) => {
              userIdentify = result_sub.asInstanceOf[String]
              send("Successfully set your identity to " + userIdentify, serverMessage = true)
              if(roomName.length>0) {
                exitChatRoom(self)
              }
            }
            case Failure(t) => t.printStackTrace
          }
        }
        case Failure(t) => send(s"There is already an user with this username! [$desiredName] Also you can show all list users <online>", serverMessage = true)
      }
    }

    def online(clientActorName: String): Unit = {
      supervisor ? GetAllClientIdentifier onComplete {
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
      supervisor ? GetAllChatRoomInfo onComplete {
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
        send(s"Already joined chat room : $roomName, if you need create room. exit room first.<exit>", serverMessage = true)
      }else {
        val name = text
        supervisor ? MakeChatRoom(actor, name) onComplete {
          case Success(result) => {
            roomName = result.asInstanceOf[String]
            if(roomName.length > 0){
              send(s"Successfully create the chatroom($roomName).", serverMessage = true)
            }
          }
          case Failure(t) => send(s"There is already an room with this roomname! [name]!, if you need join the room use <join>. Also you can show all list rooms <chatroom>", serverMessage = true)
        }
      }
    }

    def joinChatRoom(actor:ActorRef, text:String):Unit = {
      val name = text
      supervisor ? JoinChatRoom(actor, name) onComplete {
        case Success(result) => {
          roomName = result.asInstanceOf[String]
          if(roomName.length > 0){
            send(s"Successfully join the chatroom($roomName).", serverMessage = true)
          }
        }
        case Failure(t) => send("There is not exist an room name! Also you can show all list rooms <chatroom>", serverMessage = true)
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
      if(context==null || connection==null) {

      }else if (serverMessage) {
        context.actorSelection(connection.path) ? Write(makePacket("[SERVER]: " + message))
      } else {
        context.actorSelection(connection.path) ? Write(makePacket(message))
      }
    }

    def makePacket(message: String): ByteString = {
      implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

//      log.info(s"1: ${_typeOfSerializer.id.asInstanceOf[Byte]}, 2:${message},  3: ${ByteString(message)}")
      var msg = ByteString(message,"UTF-8")
      val serializedMsg = ByteString.newBuilder.putByte(_typeOfSerializer.id.asInstanceOf[Byte]).result() ++ msg
      val packet = ByteString.newBuilder
        .putInt(serializedMsg.length)
        .result() ++ serializedMsg
//      log.info(s"send message :${packet.length} : ${msg.length} : ${packet}")
      packet
    }

    def ReceiveData(data:ByteString): ByteString = {

      _typeOfSerializer = SERIALIZER.withNameOpt(data.iterator.getByte.toInt) getOrElse SERIALIZER.ROW
//      log.info(s"ReceiveData : ${data.iterator.getByte.toInt}, _typeOfSerializer : ${_typeOfSerializer}, len : ${data.utf8String}, data : ${data}")
      val rem = data drop 1 // Pop off 1byte (serializer type)

      _typeOfSerializer match {
        case SERIALIZER.ROW => {
          rem
        }
        case SERIALIZER.JSON => {
          ByteString("not support yet", "UTF-8")
        }
        case SERIALIZER.ZEROF => {
          rem
        }
      }
    }
  }