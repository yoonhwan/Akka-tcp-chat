
package chatapp.server

import java.net.InetSocketAddress
import java.nio.ByteOrder

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.io.Tcp
import akka.pattern.ask
import akka.util.{ByteString, CompactByteString, Timeout}
import chatapp.client.SERIALIZER
import server.RoomSupervisor.GetAllChatRoomInfo

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
    var roomInfo:Tuple2[String,ActorRef] = Tuple2("",null)
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
        stopProc()
      case Tcp.Aborted =>
        log.info("connection aborted")
        stopProc()
      case Tcp.ConfirmedClosed =>
        log.info("connection ConfirmedClosed")
        stopProc()
      case Tcp.Closed =>
        log.info("connection Closed")
        stopProc()
      case _: Unbound =>
        log.info("connection Unbound")
        stopProc()
    }
    
    def writing(buf: ByteString): Receive = common orElse {   
      case SendServerMessage(message) => {
        send(message, true)
      }
      case SendAllClientMessage(serializer, clientActorName, message) => {
        if(clientActorName != userIdentify)
          sendFromRoom(s"from <$clientActorName> user message : $message", false, serializer)
      }
      case SendRoomClientMessage(serializer, roomName, clientActorName, message) => {
        if(roomName.length <= 0)
          sendFromRoom("Please create or join room yourself using <create or join|[name]> Also you can show all list rooms <chatroom>", true, serializer)
        else
          if(clientActorName != userIdentify)
//            send(s"from <$clientActorName> user message at room <$roomName> : $message", false)
            sendFromRoom(s"$message", false, serializer)
      }

      case Received(data) =>

//        implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
//        val header = data.iterator.getInt
//        val temp = data drop 4
//        val serial = temp.iterator.getByte.toInt
//        log.info(s"recv message : ${data.utf8String.length} header : ${header} type : ${serial} data = ${data.utf8String} byteData = ${data}")

        val msg = buf ++ data
        val (pkt, remainder) = getPacket(msg)
        // Do something with your packet
        pkt.foreach(f=>ProccessData(ReceiveData(f)))
        context become writing(remainder) 

      case GetClientInfomation =>
        sender() ! ClientInfomation(userIdentify, remote, roomInfo._1)

      case DefaultRoomActor.DestroyDefaultRoomActor =>
        roomInfo = Tuple2("",null)

      case SendErrorMessage(error) =>
        send(s"[ERROR] ${error}", false)
    }

    override def postStop(): Unit = {
      super.postStop()
      log.info(s"stoped actor user : $userIdentify from/to [$remote]")
    }

    def stopProc(): Unit = {
      if(roomInfo._1.length>0) {
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
          if(roomInfo._1.length <= 0)
            supervisor ! SendAllClientMessage(_typeOfSerializer, userIdentify, message)
          else
            roomInfo._2 ! SendRoomClientMessage(_typeOfSerializer, roomInfo._1, userIdentify, message)
        }else {
          if(roomInfo._1.length <= 0)
            send("Please create or join room yourself using <create or join|[name]> Also you can show all list rooms <chatroom>", serverMessage = true)
          else
            roomInfo._2 ! SendRoomClientMessage(_typeOfSerializer, roomInfo._1, userIdentify, message)
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
              if(roomInfo._1.length>0) {
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
      if(roomInfo._1.length > 0) {
        send(s"Already joined chat room : $roomInfo._1, if you need create room. exit room first.<exit>", serverMessage = true)
      }else {
        val name = text
        supervisor ? MakeChatRoom(actor, name) onComplete {
          case Success(result) => {
            roomInfo = result.asInstanceOf[Tuple2[String, ActorRef]]
            if(roomInfo._1.length > 0){
              send(s"Successfully create the chatroom($roomInfo._1).", serverMessage = true)
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
          roomInfo = result.asInstanceOf[Tuple2[String, ActorRef]]
          if(roomInfo._1.length > 0){
            send(s"Successfully join the chatroom($roomInfo._1).", serverMessage = true)
          }
        }
        case Failure(t) => send("There is not exist an room name! Also you can show all list rooms <chatroom>", serverMessage = true)
      }
    }

    def exitChatRoom(actor:ActorRef):Unit = {

      if(roomInfo._1.length <= 0) {
        send(s"Does not joined any room. doesn't joined any rooms", serverMessage = true)
      }else {
        supervisor ? ExitChatRoom(actor, roomInfo._1) onComplete {
          case Success(result) => {
            send(s"Successfully exit the chatroom($roomInfo._1).", serverMessage = true)
            roomInfo = Tuple2("",null)
          }
          case Failure(t) => t.printStackTrace
        }
      }
    }

    def send(message: String, serverMessage: Boolean = false) = {
      if(context==null || connection==null) {

      }else if (serverMessage) {
        context.actorSelection(connection.path) ? Write(makePacket(SERIALIZER.ROW, "[SERVER]: " + message, true))
      } else {
        context.actorSelection(connection.path) ? Write(makePacket(_typeOfSerializer, message))
      }
    }

    def sendFromRoom(message: String, serverMessage: Boolean = false, typeOfSerializer:SERIALIZER.Value) = {
      if(context==null || connection==null) {

      }else if (serverMessage) {
        context.actorSelection(connection.path) ? Write(makePacket(typeOfSerializer, "[SERVER]: " + message, true))
      } else {
        context.actorSelection(connection.path) ? Write(makePacket(typeOfSerializer, message))
      }
    }

    def makePacket(typeOfSerializer:SERIALIZER.Value, message: String, serverMessage: Boolean = false): ByteString = {
      implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

//      log.info(s"1: ${_typeOfSerializer.id.asInstanceOf[Byte]}, 2:${message},  3: ${ByteString(message)}")
      var msg = ByteString(message,"UTF-8")
      var serializedMsg:ByteString = ByteString()
      if(serverMessage) {
        serializedMsg = ByteString.newBuilder.putByte(SERIALIZER.ROW.id.asInstanceOf[Byte]).result() ++ msg
      }else {
        serializedMsg = ByteString.newBuilder.putByte(typeOfSerializer.id.asInstanceOf[Byte]).result() ++ msg
      }
      val packet = ByteString.newBuilder
        .putInt(serializedMsg.length)
        .result() ++ serializedMsg
//      log.info(s"send message :${packet.length} : ${msg.length} : ${packet}")
      packet
    }

    def ReceiveData(data:ByteString): ByteString = {

//      log.info(s"ReceiveData : ${data.iterator.getByte.toInt}, len : ${data.utf8String}, data : ${data}")

      try {
        _typeOfSerializer = SERIALIZER.withNameOpt(data.iterator.getByte.toInt) getOrElse SERIALIZER.ROW

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
      } catch {
        case e:Exception => {
          e.printStackTrace
          log.info(s"ReceiveData : ${data.iterator.getByte.toInt}, len : ${data.utf8String}, data : ${data}")
          null
        }
      }

    }
  }