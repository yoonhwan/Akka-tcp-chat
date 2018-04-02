package chatapp.server
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
/**
  * Created by Niels Bokmans on 30-3-2016.
  */
object ClientHandlerMessages {

    case class SendMessage(message: String)
    
    
}
