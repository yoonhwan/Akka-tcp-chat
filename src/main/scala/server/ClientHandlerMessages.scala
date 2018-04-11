package chatapp.server
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
/**
  * Created by yoonhwan on 02-4-2018
  */
object ClientHandlerMessages {

    case class SendServerMessage(message: String)
    case class SendAllClientMessage(clientActorName:String, message: String)
    case class SendRoomClientMessage(roomName:String, clientActorName:String, message: String)
    
    case class SendErrorMessage(error: String)
}
