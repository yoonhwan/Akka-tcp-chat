package chatapp.server
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated}
object ClientHandlerSupervisor {
    def props(): Props = Props(classOf[ClientHandlerSupervisor])

    case class GeneratedClientHandlerActor(obj: ActorRef)
    case class DisconnectedClientHandlerActor(actorName: String)
    case class HasIdentifier(actorName: String, desireName: String)
    case class SetIdentifier(actorName: String, desireName: String)
    case object GetAllClintIdentifier
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
        case p: Props => {
            val actor = context.actorOf(p)
            sender() ! GeneratedClientHandlerActor(actor)
            context watch actor
        }
        case obj: Terminated => 
            log.info(obj + " : Terminated")

        case HasIdentifier(actorName, desireName) => {
            log.info("HasIdentifier : " + actorName)

            var hasKey: Boolean = false
            ClientIdentities.keys.takeWhile(_ => hasKey==false).foreach{ i =>  
                if(ClientIdentities(i) == desireName)
                    hasKey = true
            }

            if (hasKey) {
                sender() ! desireName
            } else {
                sender() ! ""
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
        case GetAllClintIdentifier => {
            if (ClientIdentities.isEmpty) 
                sender() ! "nobody (0 users total)."
            else 
                sender() ! ClientIdentities.values.reduce(_ + ", " + _) + " (" + ClientIdentities.size + " users total)."
        }
    }
    // override default to kill all children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}
}