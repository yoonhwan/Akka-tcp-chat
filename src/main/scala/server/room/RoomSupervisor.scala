package chatapp.server.room

import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import chatapp.server.Util
import chatapp.server.client.ClientHandlerSupervisor.GetAllClientIdentifier

import scala.collection.mutable.HashMap
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object RoomSupervisor {
  def props(): Props = Props(classOf[RoomSupervisor])

  case class CreateDefaultRoom(actor:ActorRef, roomName:String)

  case class DestroyDefaultRoom(roomInfo:Tuple2[String,ActorRef])
  case object ClearAllDefaultRoom
  case class GetActiveRoom(roomName:String)

  case object GetAllChatRoomInfo
}
class RoomSupervisor extends Actor with ActorLogging{
  val timeout = 5 seconds
  implicit val t = Timeout(timeout)
  import DefaultRoomActor._
  import RoomSupervisor._
  val ActiveRooms = HashMap.empty[String, ActorRef]

  val destroyActorId = 9999
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: ArithmeticException      ⇒ Resume
      case _: NullPointerException     ⇒ Restart
      case _: IllegalArgumentException ⇒ Stop
      case _: Exception                ⇒ Resume
    }

  override def postStop(): Unit = {


    ActiveRooms.foreach(f => {
      val room = f._2
      context.actorSelection(room.path) ! DestroyDefaultRoomActor
      room ! PoisonPill
    })
    ActiveRooms.clear

    super.postStop()
  }
  override def receive: Receive = {
    case CreateDefaultRoom(actor, roomName) =>{

      ActiveRooms += (roomName -> context.actorOf(DefaultRoomActor.props(roomName), roomName))
      val room = getActiveRoom(roomName)
      sender ! room
    }

    case DestroyDefaultRoom(roomInfo) => {
      val future = roomInfo._2 ! DestroyDefaultRoomActor
      roomInfo._2 ! PoisonPill
      ActiveRooms -= roomInfo._1
    }

    case ClearAllDefaultRoom => {
      ActiveRooms.foreach(f => {
        val room = f._2
        context.actorSelection(room.path) ! DestroyDefaultRoomActor
        room ! PoisonPill
      })
      ActiveRooms.clear
    }

    case GetActiveRoom(roomName) => {
      sender ! getActiveRoom(roomName)
    }


    case GetAllChatRoomInfo => {
      if (ActiveRooms.size == 0)
        sender ! "norooms (0 rooms total)."
      else {
        val map = ActiveRooms map{
          case (key, value) => (key -> context.actorSelection(value.path) ? GetAllClientIdentifier)
        }

        if(map.contains("globalRoom"))
          map.remove("globalRoom")

        if (map.size > 0) {
          val fut = Util.sequenceMap(map)

          fut onComplete {
            case Success(m) => //log.info("future test : " + m)
            case Failure(ex) => ex.printStackTrace()
          }
          val result = Await result(fut, timeout)

          var roomDataTotal = scala.collection.mutable.ListBuffer[String]()
          result foreach (value => {
            val data = value._2.asInstanceOf[String]
            roomDataTotal += s"{roomName:${value._1},{userNames:[${data}]}"
          })
//          log.info(roomDataTotal.toList.reduce(_ + ", " + _) + " (" + roomDataTotal.size + " rooms total).")
          sender ! roomDataTotal.toList.reduce(_ + ", " + _) + " (" + roomDataTotal.size + " rooms total)."
        }else {
          sender ! "norooms (0 rooms total)."
        }

      }
    }
    case _=>
  }

  def getActiveRoom(roomName:String) = {
    val room = Tuple2(roomName,ActiveRooms.get(roomName).getOrElse(null))
    room
  }
}
