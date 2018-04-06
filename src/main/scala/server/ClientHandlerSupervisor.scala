package chatapp.server
import akka.actor.{Actor, ActorRef, Props, ActorLogging, ActorSystem, Terminated, PoisonPill}
import akka.routing._
import scala.concurrent.{Await,Future,Promise}
import scala.util.{Success,Failure}
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.HashMap

object ClientHandlerSupervisor {
    def props(): Props = Props(classOf[ClientHandlerSupervisor])

    case class GeneratedClientHandlerActor(obj: ActorRef)
    case class DisconnectedClientHandlerActor(obj: ActorRef)
    case class HasIdentifier(actorName: String, desireName: String)
    case class SetIdentifier(actorName: String, desireName: String)
    case class GetAllClientIdentifier()

    case class MakeChatRoom(actor: ActorRef, roomName: String)
    case class JoinChatRoom(actor: ActorRef, roomName: String)
    case class ExitChatRoom(actor: ActorRef, roomName: String)
    case class GetAllChatRoomInfo(s:ActorRef)
    case class ClearAllChatRoom(s:ActorRef)

}

class ClientHandlerSupervisor extends Actor with ActorLogging{
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    import DynamicGroupRouter._
    implicit val timeout = Timeout(5 seconds)
    
    val ClientIdentities = HashMap.empty[String, String]
    val ActiveRooms = HashMap.empty[String, ActorRef]
    
    val globalRoom = context.actorOf(DynamicGroupRouter.props("globalRoom"), "globalRoom")
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
            globalRoom ! AddRouteeActor(actor)
            context watch actor
        }
        case Terminated(obj) => 
            // log.info(obj + " : Terminated")
            self ! DisconnectedClientHandlerActor(obj)

        case HasIdentifier(actorName, desireName) => {
            // log.info("HasIdentifier : " + actorName)

            var hasKey: Boolean = false
            ClientIdentities.keys.takeWhile(_ => hasKey==false).foreach{ i =>  
                if(ClientIdentities(i) == desireName)
                    hasKey = true
            }

            if (hasKey == false) {
                sender() ! desireName
            } else {
                sender() ! akka.actor.Status.Failure(new Exception("already exist user"))
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
            globalRoom ! RemoveRouteeActor(obj)
            if (ClientIdentities.contains(obj.path.name)) 
            {
                self ! SendMessage("", "<" + ClientIdentities.get(obj.path.name).get + "> has left the chatroom.", true)
                ClientIdentities -= obj.path.name
            }       
        }
        case GetAllClientIdentifier() => {
            if (ClientIdentities.isEmpty) 
                sender() ! "nobody (0 users total)."
            else 
                sender() ! ClientIdentities.values.reduce(_ + ", " + _) + " (" + ClientIdentities.size + " users total)."
        }
        case SendMessage(clientActorName, message, serverMessage) =>
            globalRoom ! SendMessage(clientActorName, message, serverMessage)

        case GetAllChatRoomInfo(s) => {
            if (ActiveRooms.isEmpty) 
                s ! "norooms (0 rooms total)."
            else {
                val taskFutures: HashMap[String, Future[Any]] = ActiveRooms map{
                    case (key, value) => (key, context.actorSelection(value.path) ? new GetAllClientIdentifier())
                }
                val searchFuture: Future[HashMap[String,Any]] = Util.combineFutures(taskFutures)
                val result = Await result (searchFuture, 2 seconds)

                var roomDataTotal = scala.collection.mutable.ListBuffer[String]()
                result foreach(value => {
                    val data = value._2.asInstanceOf[String]
                    roomDataTotal += s"{roomName:${value._1}:[${data}]"
                })

                log.info(roomDataTotal.toList.reduce(_ + ", " + _) + " (" + roomDataTotal.size + " rooms total).")
                s ! roomDataTotal.toList.reduce(_ + ", " + _) + " (" + roomDataTotal.size + " rooms total)."


                alja;sljdhlsahj
            }
        }

        case MakeChatRoom(actor, roomName) => {
            
            val room:ActorRef = getActiveRoom(roomName)
            if(room != null)
            {
                sender() ! akka.actor.Status.Failure(new Exception("already exist chatroom"))
            }else {
                createRoom(actor,roomName)
                sender ! roomName
            }
        }

        case JoinChatRoom(actor, roomName) => {
            // self ! SendMessage("", "<" + ClientIdentities.get(actorname).get + "> has joined the chatroom.", true)
            val localsender = sender()
            val room:ActorRef = getActiveRoom(roomName)
            if(room != null)
            {
                joinTheRoom(actor,roomName)
                sender ! roomName
            }else
                localsender ! akka.actor.Status.Failure(new Exception("not exist room error"))
        }

        case ExitChatRoom(actor, roomName) => {
            val localsender = sender()
            val room:ActorRef = getActiveRoom(roomName)
            if(room != null)
            {
                room ? GetRoomUserCount onComplete {
                    case Success(result) => {
                        val count = result.asInstanceOf[Int]
                        removeActiveRoomAndDestroy(actor, roomName, count)
                        localsender ! s"Successfully exit the chatroom($roomName) remain user count : ${count-1}."
                    }
                    case Failure(t) => localsender ! akka.actor.Status.Failure(t)
                }
            }else
                localsender ! akka.actor.Status.Failure(new Exception("exitchatroom error"))
        }
        case ClearAllChatRoom(sender) => {
            ActiveRooms.foreach(f => {
                val room = f._2
                context.actorSelection(room.path) ? DestroyGroupRouter onComplete {
                    case Success(result) => {
                        context stop room
                    }
                    case Failure(t) => t.printStackTrace
                }
            })
            ActiveRooms.clear
        }
    }

    def getActiveRoom(roomName:String):ActorRef = {
        ActiveRooms get roomName getOrElse null
    }
    
    def createRoom(actor:ActorRef, roomName:String) = {
        ActiveRooms += (roomName -> context.actorOf(DynamicGroupRouter.props(roomName), roomName))
        val room:ActorRef = getActiveRoom(roomName)
        if(room != null)
        {
            getActiveRoom(roomName) ! AddRouteeActor(actor)
        }
    }

    def joinTheRoom(actor:ActorRef, roomName:String) = {
        val room:ActorRef = getActiveRoom(roomName)
        getActiveRoom(roomName) ! AddRouteeActor(actor)
    }

    def removeActiveRoomAndDestroy(actor:ActorRef, roomName:String, count:Int) = {
        val room:ActorRef = getActiveRoom(roomName)
        if(room != null)
        {
            room ! RemoveRouteeActor(actor)
            
            if (count-1 <= 0)
            {
                room ? DestroyGroupRouter onComplete {
                    case Success(result) => {
                        context stop room
                        ActiveRooms -= roomName
                    }
                    case Failure(t) => t.printStackTrace
                }
            }
        }
    }
    // override default to kill all children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}
}