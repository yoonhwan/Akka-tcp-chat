package chatapp.server
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
/**
  * Created by Niels Bokmans on 30-3-2016.
  */
object ClientHandlerMessages {

    case class SendServerMessage(message: String)
    case class SendAllClientMessage(clientActorName:String, message: String)
    case class SendRoomClientMessage(roomName:String, clientActorName:String, message: String)
    
    
}
