
package chatapp.server.client

import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.util.ByteString

import akka.pattern.ask
import scala.concurrent.Await

object GameClientState {
  sealed trait GameClientState
  case object NONE extends GameClientState
  case object READY extends GameClientState
  case object PLAY extends GameClientState
  case object WATCH extends GameClientState
  def fromString(value: String): Option[GameClientState] = {
    Vector(NONE, READY, PLAY, WATCH).find(_.toString == value)
  }

  def fromIndex(value: Int): Option[GameClientState] = {
    value match {
      case 0 => Option(NONE)
      case 1 => Option(READY)
      case 2 => Option(PLAY)
      case 3 => Option(WATCH)
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
  import chatapp.server.room.FirstGameRoomActor._
  import chatapp.server.room.FirstGameRoomState
  import GameClientActor._

  var _roomState : FirstGameRoomState.RoomState = FirstGameRoomState.NONE
  var _clientState : GameClientState.GameClientState = GameClientState.NONE
  var _currentScore:Long = 0
  var _startTime: Long = Long.MinValue
  var _finishTime: Long = Long.MaxValue

  override def receive: Receive = super.receive orElse {
    case GetClientState() => {
      sender() ! (userIdentify, _clientState)
    }
    case _ =>{

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
          _clientState = result.asInstanceOf[GameClientState.GameClientState]

          send("clientstatechanged|" + GameClientState.toInt(_clientState).toString())
        }else {
          commonErrorMessage()
        }
        true
      case "gameready" => {
        if(roomInfo._2 != null) {
          val future = roomInfo._2 ? GameReady(self, userIdentify)
          Await.result(future, _timeOut)
          _clientState = GameClientState.READY
          send("clientstatechanged|" + GameClientState.toInt(_clientState).toString())
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
    send("Please create or join room yourself using <create or join|[name]> Also you can show all list rooms <chatroom>", serverMessage = true)
  }
}