package chatapp.server
import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object DynamicGroupRouter   {
    def props(roomName:String): Props = Props(classOf[DynamicGroupRouter], roomName)

    case class AddRouteeActor(actor: ActorRef)
    case class RemoveRouteeActor(actor: ActorRef)
    case object GetRoomUserCount
    case object DestroyGroupRouter
}
class DynamicGroupRouter(roomName:String) extends Actor with ActorLogging{
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    import DynamicGroupRouter._
    implicit val timeout = Timeout(5 seconds)
    
    val ActiveClients = scala.collection.mutable.HashMap.empty[ActorPath, ActorRef]
    
    //restart child
    override def preStart() {
        super.preStart()
        ActiveClients.clear
    }

    def receive :Receive = {
        case AddRouteeActor(actor) => 
            ActiveClients += (actor.path -> actor)
        
        case server @ SendServerMessage(message) => {
            try {
                ActiveClients.foreach(f => {
                    context.actorSelection(f._1) ! server
                })
            } catch {
                case e:Exception => e.printStackTrace
            }
        }

        case all @ (clientActorName, message) => {
            try {
                ActiveClients.foreach(f => {
                    context.actorSelection(f._1) ! all
                })
            } catch {
                case e:Exception => e.printStackTrace
            }
        }
        case m @ SendRoomClientMessage(serializer, roomName, clientActorName, message) => {
            try {
                ActiveClients.foreach(f => {
                    context.actorSelection(f._1) ! m
                })
            } catch {
                case e:Exception => e.printStackTrace
            }
        }
            
        case RemoveRouteeActor(actor) => 
            ActiveClients -= actor.path

        case GetAllClientIdentifier => {
            if (ActiveClients.isEmpty) 
                sender ! "nobody (0 users total)."
            else {
                val taskFutures: List[Future[Any]] = ActiveClients.map(f => {
                    context.actorSelection(f._1) ? ClientHandlerActor.GetClientInfomation
                }).toList
                val searchFuture: Future[List[Any]] = Util.sequenceList(taskFutures)
                val result = Await result (searchFuture, 2 seconds)

                var userdataTotal = scala.collection.mutable.ListBuffer[String]()
                result foreach(value => {
                    val data = value.asInstanceOf[ClientHandlerActor.ClientInfomation]
                    userdataTotal += s"${data.userIdentify}"
                })
                sender ! userdataTotal.toList.reduce(_ + ", " + _)
            }
        }

        case GetRoomUserCount => {
            sender() ! ActiveClients.size
        }

        case DestroyGroupRouter => {
            ActiveClients.foreach(f => {
                context.actorSelection(f._1) ! DestroyGroupRouter
            })
            ActiveClients.clear
            sender ! ""
        }
    }

}