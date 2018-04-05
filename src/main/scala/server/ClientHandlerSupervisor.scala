package chatapp.server
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated, PoisonPill}
import akka.routing._

object ClientHandlerSupervisor {
    def props(): Props = Props(classOf[ClientHandlerSupervisor])

    case class GeneratedClientHandlerActor(obj: ActorRef)
    case class DisconnectedClientHandlerActor(obj: ActorRef)
    case class HasIdentifier(actorName: String, desireName: String)
    case class SetIdentifier(actorName: String, desireName: String)
    case object GetAllCleintIdentifier
}

class ClientHandlerSupervisor extends Actor with ActorLogging{
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    import DynamicGroupRouter._

    val ClientIdentities = scala.collection.mutable.HashMap.empty[String, String]

    val router = context.actorOf(DynamicGroupRouter.props(), "router")
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
            router ! AddRouteeActor(actor)
            context watch actor
        }
        case Terminated(obj) => 
            // log.info(obj + " : Terminated")
            router ! DisconnectedClientHandlerActor(obj)

        case HasIdentifier(actorName, desireName) => {
            // log.info("HasIdentifier : " + actorName)

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
            // log.info("SetIdentifier : [" + actorname + " : " + desirename + "]")
            ClientIdentities += (actorname -> desirename)
            sender() ! desirename
            self ! SendMessage("", "<" + ClientIdentities.get(actorname).get + "> has joined the chatroom.", true)
        }
        case DisconnectedClientHandlerActor(obj) => {
            // log.info("DisconnectedClientHandlerActor : " + obj.path.name)
            context.stop(obj)
            router ! RemoveRouteeActor(obj)
            if (ClientIdentities.contains(obj.path.name)) 
            {
                self ! SendMessage("", "<" + ClientIdentities.get(obj.path.name).get + "> has left the chatroom.", true)
                ClientIdentities -= obj.path.name
            }       
        }
        case GetAllCleintIdentifier => {
            if (ClientIdentities.isEmpty) 
                sender() ! "nobody (0 users total)."
            else 
                sender() ! ClientIdentities.values.reduce(_ + ", " + _) + " (" + ClientIdentities.size + " users total)."
        }
        case SendMessage(clientActorName, message, serverMessage) =>
            router ! SendMessage(clientActorName, message, serverMessage)
    }
    // override default to kill all children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}
}