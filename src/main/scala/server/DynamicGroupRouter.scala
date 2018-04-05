package chatapp.server
import akka.actor.{Actor, ActorRef, ActorPath, Props, ActorLogging, ActorSystem, Terminated, PoisonPill}


object DynamicGroupRouter   {
    def props(): Props = Props(classOf[DynamicGroupRouter])

    case class AddRouteeActor(actor: ActorRef)
    case class RemoveRouteeActor(actor: ActorRef)
}
class DynamicGroupRouter() extends Actor with ActorLogging{
    import ClientHandlerMessages._
    import DynamicGroupRouter._
    val ActiveClients = scala.collection.mutable.HashMap.empty[ActorPath, ActorRef]

    //restart child
    override def preStart() {
        super.preStart()
        ActiveClients.clear
    }

    def receive :Receive = {
        case AddRouteeActor(actor) => 
            ActiveClients += (actor.path -> actor)
            
        case msg : SendMessage => 
            try {
                ActiveClients.foreach(f => {
                    context.actorSelection(f._1) ! msg
                })
            } catch {
                case e:Exception => e.printStackTrace
            }
            
            
        case RemoveRouteeActor(actor) => 
            ActiveClients -= actor.path
    }
}