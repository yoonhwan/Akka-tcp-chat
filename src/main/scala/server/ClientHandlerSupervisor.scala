package chatapp.server
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
object ClientHandlerSupervisor {
    def props(): Props = Props(classOf[ClientHandlerSupervisor])

    case class GeneratedClientHandlerActor(obj: ActorRef)
    case class DisconnectedClientHandlerActor(actorName: String)
    case class HasIdentifier(actorName: String)
    case class SetIdentifier(actorName: String, desireName: String)
}

class ClientHandlerSupervisor extends Actor with ActorLogging{
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    
    val ClientIdentities = scala.collection.mutable.HashMap.empty[String, String]

    override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: ArithmeticException      ⇒ Resume
        case _: NullPointerException     ⇒ Restart
        case _: IllegalArgumentException ⇒ Stop
        case _: Exception                ⇒ Escalate
    }

    def receive = {
        case p: Props => sender() ! GeneratedClientHandlerActor(context.actorOf(p))
        case HasIdentifier(actorName) => {
            log.info("HasIdentifier : " + actorName)
            if (!ClientIdentities.contains(actorName)) {
                sender() ! ""
            } else {
                sender() ! ClientIdentities.get(actorName).get
            }
        }
        case SetIdentifier(actorname, desirename) => {
            log.info("SetIdentifier : [" + actorname + " : " + desirename + "]")
            ClientIdentities += (actorname -> desirename)
            sender() ! desirename
        }
        case DisconnectedClientHandlerActor(actorName) => {
            // sendToAll("", "<" + ClientIdentities.get(clientActorName).get + "> has left the chatroom.", serverMessage = true)
            log.info("DisconnectedClientHandlerActor : " + actorName)
            if (ClientIdentities.contains(actorName)) 
                ClientIdentities -= actorName
        }
    }
    // override default to kill all children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}
}