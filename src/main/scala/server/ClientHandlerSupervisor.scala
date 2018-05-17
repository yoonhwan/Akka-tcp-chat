package chatapp.server
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object ClientHandlerSupervisor {
    def props(): Props = Props(classOf[ClientHandlerSupervisor])

    case class GeneratedClientHandlerActor(obj: ActorRef)
    case class DisconnectedClientHandlerActor(obj: ActorRef)
    case class HasIdentifier(actorName: String, desireName: String)
    case class SetIdentifier(actorName: String, desireName: String)
    case object GetAllClientIdentifier

    case class MakeChatRoom(actor: ActorRef, roomName: String)
    case class JoinChatRoom(actor: ActorRef, roomName: String)
    case class ExitChatRoom(actor: ActorRef, roomName: String)
    case object GetAllChatRoomInfo
    case object ClearAllChatRoom

}

class ClientHandlerSupervisor extends Actor with ActorLogging{
    import ClientHandlerMessages._
    import ClientHandlerSupervisor._
    import DynamicGroupRouter._
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._

    import scala.concurrent.duration._
    implicit val timeout = Timeout(5 seconds)

    val ActiveRooms = HashMap.empty[String, ActorRef]

    var globalRoom = context.actorOf(DynamicGroupRouter.props("globalRoom"), "globalRoom")
    val redisSupportActor = context.actorOf(RedisSupportActor.props(), "RedisSupportActor")
    override def postStop(): Unit = {
        context.stop(globalRoom)
    }

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
        case DisconnectedClientHandlerActor(obj) => {
            // log.info("DisconnectedClientHandlerActor : " + obj.path.name)
            context.stop(obj)
            globalRoom ! RemoveRouteeActor(obj)
            val actorname = getClientname(obj.path.name)
            if(actorname.length >= 0) {
                val redis = RedisSupportActor.redis.getOrElse(null)
                if (redis != null) {
                    self ! SendServerMessage(s"<${actorname}> has left the <global> chatroom. exit chat")
                    val del1 = redis.del("active:user:actorname:" + obj.path.name)
                    val del2 = redis.del("active:user:desirename:" + actorname)
                    Await.result(for {
                        s1 <- del1
                        s2 <- del2
                    } yield {
                        s1
                    }, 5 seconds)
                }
            }
        }
        case HasIdentifier(actorName, desireName) => {
            // log.info("HasIdentifier : " + actorName)
            val localsender = sender()
            var hasKey: Boolean = false

            val redis = RedisSupportActor.redis.getOrElse(null)

            if(redis != null) {
                val get = redis.get("active:user:desirename:" + desireName)
                Await.result(for {s <- get} yield {
                    val result = s.getOrElse(null)
                    if (result != null)
                        hasKey = true
                    else
                        ""
                }, 5 seconds)
            }

            if (hasKey == false) {
                localsender ! desireName
            } else {
                localsender ! akka.actor.Status.Failure(new Exception("already exist user"))
                context.actorSelection(actorName) ! SendErrorMessage("already exist user : " + desireName)
            }
        }
        case SetIdentifier(actorname, desirename) => {
            val localsender = sender()
            // log.info("SetIdentifier : [" + actorname + " : " + desirename + "]")

            val redis = RedisSupportActor.redis.getOrElse(null)
            if(redis != null)
            {
                val set1 = redis.set("active:user:actorname:"+actorname, desirename)
                val set2 = redis.set("active:user:desirename:"+desirename, actorname)
                val r :Future[Boolean] = for {
                    s1 <- set1
                    s2 <- set2
                } yield {s1}
                Await.result(r, 5 seconds)
            }

            val myname = getClientname(actorname)
            if(myname.length > 0)
                self ! SendServerMessage(s"<${myname}> has joined the <global> chatroom.")
            localsender ! myname
        }

        case GetAllClientIdentifier => {
            val redis = RedisSupportActor.redis.getOrElse(null)
            var init = 0
            var count = 0
            var loop = true
            if(redis != null) {
              while(loop) {
                val keys = redis.scan(init,Option(100),Option("active:user:desirename:*"))
                Await.result(for {s <- keys} yield {
                  init = s.index
                  count += s.data.length
                }, 5 seconds)
                if(init == 0)
                  loop = false
              }
            }
            sender() ! s"${count} users total."
        }
            
        case msg @ SendServerMessage(message) => {
            globalRoom ! msg
        }

        case SendAllClientMessage(serializer, clientActorName, message) => {
            globalRoom ! SendAllClientMessage(serializer, clientActorName, message)
        }

        case m @ SendRoomClientMessage(serializer, roomName, clientActorName, message) => {
            val room:ActorRef = getActiveRoom(roomName)
            if(room != null)
            {
                room ! m
            }else   {
                sender ! akka.actor.Status.Failure(new Exception("exitchatroom error"))
                context.actorSelection(clientActorName)  ! SendErrorMessage("exitchatroom error")
            }
        }
        case GetAllChatRoomInfo => {
            if (ActiveRooms.size == 0) 
                sender ! "norooms (0 rooms total)."
            else {
                val map = ActiveRooms map{
                    case (key, value) => (key -> context.actorSelection(value.path) ? GetAllClientIdentifier)
                }
                val fut = Util.sequenceMap(map)
                
                fut onComplete{
                    case Success(m) => //log.info("future test : " + m)
                    case Failure(ex) => ex.printStackTrace()
                }
                val result = Await result (fut, 2 seconds)

                var roomDataTotal = scala.collection.mutable.ListBuffer[String]()
                result foreach(value => {
                    val data = value._2.asInstanceOf[String]
                    roomDataTotal += s"{roomName:${value._1}:{userNames:[${data}]}"
                })
                // log.info(roomDataTotal.toList.reduce(_ + ", " + _) + " (" + roomDataTotal.size + " rooms total).")
                sender ! roomDataTotal.toList.reduce(_ + ", " + _) + " (" + roomDataTotal.size + " rooms total)."
            }
        }

        case MakeChatRoom(actor, roomName) => {
            
            val room:ActorRef = getActiveRoom(roomName)
            if(room != null)
            {
                sender() ! akka.actor.Status.Failure(new Exception("already exist chatroom"))
                context.actorSelection(actor.path.name) ! SendErrorMessage("already exist chatroom")
            }else {
                createRoom(actor,roomName)
                sender ! roomName
            }
        }

        case JoinChatRoom(actor, roomName) => {
            val localsender = sender()
            val room:ActorRef = getActiveRoom(roomName)
            if(room != null)
            {
                joinTheRoom(actor,roomName)
                sender ! roomName
                val actorname = getClientname(actor.path.name)
                if(actorname.length > 0)
                    self ! SendServerMessage(s"<${actorname}> has joined the <$roomName> chatroom.")

            }else   {
                localsender ! akka.actor.Status.Failure(new Exception("not exist room error"))
                context.actorSelection(actor.path.name) ! SendErrorMessage("not exist room error")
            }
        }

        case ExitChatRoom(actor, roomName) => {
            val localsender = sender()
            val room:ActorRef = getActiveRoom(roomName)
            val actorname = getClientname(actor.path.name)
            if(room != null)
            {
                room ? GetRoomUserCount onComplete {
                    case Success(result) => {
                        val count = result.asInstanceOf[Int]
                        removeActiveRoomAndDestroy(actor, roomName, count)
                        localsender ! s"Successfully exit the chatroom($roomName) remain user count : ${count-1}."

                        if(actorname.length > 0)
                            self ! SendServerMessage(s"<${actorname}> has left the <$roomName> chatroom. exit chat")
                
                    }
                    case Failure(t) => localsender ! akka.actor.Status.Failure(t)
                }
            }else   {
                localsender ! akka.actor.Status.Failure(new Exception("exitchatroom error"))
                context.actorSelection(actor.path.name)  ! SendErrorMessage("exitchatroom error")
            }
        }
        case ClearAllChatRoom => {
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

    def getClientname(name:String):String = {

        val redis = RedisSupportActor.redis.getOrElse(null)

        if(redis != null) {
            val get = redis.get("active:user:actorname:" + name)
            val actorname = Await.result(for {s <- get} yield {
                val result = s.getOrElse(null)
                if (result != null)
                    result.utf8String
                else
                    ""
            }, 5 seconds)

            actorname
        }else
            ""
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
    override def preRestart(cause: Throwable, msg: Option[Any]): Unit = {

    }


}