package chatapp.server.room

import akka.actor.ActorRef
import akka.util.ByteString
import chatapp.server.client.GameClientState
import chatapp.server.room.DefaultRoomActor.RemoveRouteeActor

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.Cancellable
import chatapp.server.client.ClientHandlerMessages.SendRoomClientMessage

object FirstGameRoomState {
  sealed trait RoomState
  case object NONE extends RoomState
  case object WAIT extends RoomState
  case object COUNTDOWN extends RoomState
  case object PLAY extends RoomState
  case object FINISH extends RoomState
  def fromString(value: String): Option[RoomState] = {
    Vector(NONE, WAIT, COUNTDOWN, PLAY, FINISH).find(_.toString == value)
  }

  def fromIndex(value: Int): Option[RoomState] = {
    value match {
      case 0 => Option(NONE)
      case 1 => Option(WAIT)
      case 2 => Option(COUNTDOWN)
      case 3 => Option(PLAY)
      case 4 => Option(FINISH)
    }
  }

  def toInt(value : FirstGameRoomState.RoomState): Int = {
    value match {
      case NONE => 0
      case WAIT => 1
      case COUNTDOWN => 2
      case PLAY => 3
      case FINISH => 4
    }
  }
}

object FirstGameRoomActor   {
  case class GameReady(actorRef: ActorRef, clientDesireName: String)
  case class RefreshClientState(actorRef: ActorRef, clientDesireName:String)
}

class FirstGameRoomActor(roomName:String) extends DefaultRoomActor(roomName){
  import FirstGameRoomActor._
  var _roomState : FirstGameRoomState.RoomState = FirstGameRoomState.WAIT
  var _gameStartTimer :Cancellable= null;

  override def postStop(): Unit = {
    super.postStop()
    _gameStartTimer.cancel()
  }

  context become receiveGameReady

  def receiveGameReady = gameReadyState orElse super.receive
  def gameReadyState: Receive = {
    case GameReady(actorRef, clientDesireName) => {
      if(redis != null)
      {
        log.info(s"GameReady")
        val set = redis.set(s"active:room:${roomName}:${clientDesireName}:state", GameClientState.READY.toString())
        val result = Await.result(set, timeout)
        assert(result.asInstanceOf[Boolean]==true)

        sender() ! ""

        _gameStartTimer = context.system.scheduler.schedule(0 seconds, 1 seconds)(OnTimer);
      }
    }
    case RefreshClientState(actorRef, clientDesireName) => {
      var _tempState :GameClientState.GameClientState = GameClientState.NONE
      if(redis != null)
      {
        log.info(s"RefreshClientState")

        val state = Await.result(
          redis.get(s"active:room:${roomName}:${clientDesireName}:state")
          , timeout) getOrElse(ByteString(""))

        _tempState = GameClientState.fromString(state.utf8String) getOrElse GameClientState.NONE
        sender() ! _tempState
      }

      _tempState
    }
    case RemoveRouteeActor(actor, name) =>
    {

    }
    case all @ (clientActorName, message) => {

    }
    case m @ SendRoomClientMessage(serializer, roomName, clientActorName, message) => {
//      new Exception("").printStackTrace()
    }
  }


  def OnTimer(): Unit =
  {
    log.info(s"FirstGameRoomActor work!!!!")
  }
}