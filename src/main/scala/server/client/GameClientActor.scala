
package chatapp.server.client

import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.ByteString
import chatapp.server.client.ClientHandlerActor.ClientJoinedRoom

import scala.concurrent.Await

object GameClientState {
  sealed trait GameClientState
  case object NONE extends GameClientState
  case object READY extends GameClientState
  case object PLAY extends GameClientState
  case object WATCH extends GameClientState
  def fromString(value: String): GameClientState.GameClientState = {
    Vector(NONE, READY, PLAY, WATCH).find(_.toString == value) getOrElse NONE
  }

  def fromIndex(value: Int): GameClientState.GameClientState = {
    value match {
      case 0 => NONE
      case 1 => READY
      case 2 => PLAY
      case 3 => WATCH
    }
  }

  def toInt(value : GameClientState.GameClientState): Int = {
    value match {
      case NONE => 0
      case READY => 1
      case PLAY => 2
      case WATCH => 3
    }
  }
}

object GameClientActor   {

  case class GetClientState()
  case object GetUserScoreList
  case class GetUserRankingList(start:Int, finish:Int)
}

class GameClientActor(supervisor: ActorRef, connection: ActorRef, remote: InetSocketAddress) extends ClientHandlerActor (supervisor, connection, remote){
  import GameClientActor._
  import chatapp.server.room.FirstGameRoomActor._
  import chatapp.server.room.FirstGameRoomState

  var _roomState : FirstGameRoomState.RoomState = FirstGameRoomState.NONE
  var _clientState : GameClientState.GameClientState = GameClientState.NONE
  var _currentScore:Long = 0
  var _startTime: Long = -1
  var _finishTime: Long = -1

  override def writing(buf: ByteString): Receive = super.writing(buf) orElse {
    case ClientJoinedRoom => {
      log.info(s"ClientJoinedRoom")
      val future = roomInfo._2 ? InitClientState(self, userIdentify)
      val result = Await.result(future, _timeOut)
      val data = result.asInstanceOf[Tuple5[FirstGameRoomState.RoomState, GameClientState.GameClientState, Int, Long, Long]]

      log.info(s"ClientJoinedRoom init:${data}")
    }
    case GetClientState() => {
      sender() ! (userIdentify, _clientState)
    }
    case _ =>{
      if(buf.length>0) {
        log.info(s"${buf.utf8String}")
        commonErrorMessage()
      }
    }
  }


  override def ProccessData(data: ByteString): Boolean= {
    val text = data.utf8String
    val clientActorName = self.path.name
    val splitdata = getCommand(text)

    splitdata._1 match {
      case "getrank" =>  true
      case "refreshstate" =>
        if(roomInfo._2 != null) {
          val future = roomInfo._2 ? RefreshClientState(self, userIdentify)
          val result = Await.result(future, _timeOut)
          val data = result.asInstanceOf[Tuple5[FirstGameRoomState.RoomState, GameClientState.GameClientState, Int, Long, Long]]

          log.info(s"Refreshstate init:${data}")
          _clientState = data._2

          send(s"gamestatechanged|" +
            s"${FirstGameRoomState.toInt(data._1)}|" +
            s"${GameClientState.toInt(data._2).toString()}|" +
            s"${data._3}|" +
            s"${data._4}|" +
            s"${data._5}")
        }else {
          commonErrorMessage()
        }
        true
      case "gameready" => {
        if(roomInfo._2 != null) {
          _clientState = GameClientState.READY
          val future = roomInfo._2 ? ChangeClientState(_clientState, 0, _startTime, _finishTime, self, userIdentify)
          val result = Await.result(future, _timeOut)
          val data = result.asInstanceOf[Tuple5[FirstGameRoomState.RoomState, GameClientState.GameClientState, Int, Long, Long]]

          send(s"gamestatechanged|" +
            s"${FirstGameRoomState.toInt(data._1)}|" +
            s"${GameClientState.toInt(data._2).toString()}|" +
            s"${data._3}|" +
            s"${data._4}|" +
            s"${data._5}")
        }else {
          commonErrorMessage()
        }
        true
      }
      case _ => {
        if(super.ProccessData(data))
          true
        else
          false
      }
    }

  }

  def commonErrorMessage(): Unit =
  {
    send("GameClientActor::Unknown command error", serverMessage = true)
    new Exception("Unknown command error").printStackTrace()
  }
}