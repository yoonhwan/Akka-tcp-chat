package chatapp.server.room

import akka.actor.{ActorRef, Cancellable}
import akka.pattern.ask
import akka.util.ByteString
import chatapp.client.SERIALIZER
import chatapp.server.RedisSupportActor
import chatapp.server.RedisSupportActor.HasKey
import chatapp.server.client.ClientHandlerMessages.{SendRoomClientMessage, SendServerMessage}
import chatapp.server.client.GameClientState
import chatapp.server.room.DefaultRoomActor.RemoveRouteeActor

import scala.collection.mutable.{HashMap, ListBuffer}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object FirstGameRoomState {
  sealed trait RoomState
  case object NONE extends RoomState
  case object WAIT extends RoomState
  case object COUNTDOWN extends RoomState
  case object PLAY extends RoomState
  case object FINISH extends RoomState
  def fromString(value: String): RoomState = {
    Vector(NONE, WAIT, COUNTDOWN, PLAY, FINISH).find(_.toString == value) getOrElse NONE
  }

  def fromIndex(value: Int): RoomState = {
    value match {
      case 0 => NONE
      case 1 => WAIT
      case 2 => COUNTDOWN
      case 3 => PLAY
      case 4 => FINISH
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
  case class InitClientState(actorRef: ActorRef, clientDesireName: String)
  case class ChangeClientState(
                                _clientState:GameClientState.GameClientState,
                                _score: Int,
                                _start: Long,
                                _finish: Long,
                                actorRef: ActorRef, clientDesireName: String)
  case class RefreshClientState(actorRef: ActorRef, clientDesireName:String)


  //common
  case class SetNetworkTimeOut(_time: Int)
}

class FirstGameRoomActor(roomName:String) extends DefaultRoomActor(roomName){
  import FirstGameRoomActor._
  var _roomState : FirstGameRoomState.RoomState = FirstGameRoomState.WAIT
  var _gameStartTimer :Cancellable= null
  var _gameRoomStateTimer : Cancellable = null
  val _exitCheckSchduler = HashMap.empty[String, akka.actor.Cancellable]
  val _userExitWaitTime = 10

  _gameRoomStateTimer = context.system.scheduler.schedule(1 seconds, 1 seconds)(OnRoomDataShareTimer);
  override def postStop(): Unit = {
    if(_gameStartTimer!=null)
      _gameStartTimer.cancel()
    if(_gameRoomStateTimer!=null)
      _gameRoomStateTimer.cancel()

    if(_exitCheckSchduler.size > 0) {
      _exitCheckSchduler.foreach(f => {
        f._2.cancel()
      })
    }

    super.postStop()
  }

  context become waitGameReady

  def waitGameReady = gameReadyState orElse super.receive

  def gameCommon: Receive = {
    case SetNetworkTimeOut(_time) => {


    }
  }

  def gameReadyState: Receive = gameCommon orElse {
    case InitClientState(actorRef, clientDesireName) => {
      if(redis != null)
      {
        log.info(s"InitClientState")
        val state = GameClientState.toInt(GameClientState.NONE)
        val score = 0
        val starttime = -1
        val finishtime = -1

        val hasKey = Await.result(
          RedisSupportActor.inst ? HasKey(s"active:room:${roomName}:userstate:${clientDesireName}:*")
          ,timeout).asInstanceOf[Boolean]

        if(hasKey == false) {

          val result = Await.result(for {
            a<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:state", state)
            b<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:score", score)
            c<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:start", starttime)
            d<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:finish", finishtime)
            d<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:connected", 1)
          }yield{
            d
          }, timeout)
          assert(result.asInstanceOf[Boolean]==true)
        }

        sender() ! Tuple5(_roomState, GameClientState.fromIndex(state), score, starttime, finishtime)
      }
    }

    case ChangeClientState(_clientState, _score, _start, _finish, actorRef, clientDesireName) => {
      if(redis != null)
      {
        log.info(s"ChangeClientState")
        val hasKey = Await.result(
          RedisSupportActor.inst ? HasKey(s"active:room:${roomName}:userstate:${clientDesireName}:*")
          ,timeout).asInstanceOf[Boolean]

        if(hasKey == false) {
          assert(false)
        }else {
          val result = Await.result(for {
            a<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:state", GameClientState.toInt(_clientState))
            b<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:score", _score)
            c<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:start", _start)
            d<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:finish", _finish)
            d<-redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:connected", 1)
          }yield{
            b
          }, timeout)
        }
        val result = Await.result(for {
          a<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:state")
          b<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:score")
          c<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:start")
          d<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:finish")
        }yield{
          Tuple5(_roomState, GameClientState.fromIndex(a.get.utf8String.toInt), b.get.utf8String.toInt, c.get.utf8String.toLong, d.get.utf8String.toLong)
        }, timeout)

        sender() ! result

        if(_gameStartTimer != null)
          _gameStartTimer.cancel()

        _gameStartTimer = context.system.scheduler.schedule(0 seconds, 1 seconds)(OnTimer);
      }
    }

    case RefreshClientState(actorRef, clientDesireName) => {
      if(redis != null)
      {
        log.info(s"RefreshClientState")
        val result = Await.result(for {
          a<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:state")
          b<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:score")
          c<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:start")
          d<-redis.get(s"active:room:${roomName}:userstate:${clientDesireName}:finish")
        }yield{
          redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:state", a.get.utf8String.toInt)
          redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:score", b.get.utf8String.toInt)
          redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:start", c.get.utf8String.toLong)
          redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:finish", d.get.utf8String.toLong)
          redis.set(s"active:room:${roomName}:userstate:${clientDesireName}:connected", 1)

          Tuple5(_roomState, GameClientState.fromIndex(a.get.utf8String.toInt), b.get.utf8String.toInt, c.get.utf8String.toLong, d.get.utf8String.toLong)
        }, timeout)

        sender() ! result
      }else
        sender() ! Tuple5(_roomState, GameClientState.NONE, 0, Long.MinValue, Long.MaxValue)
    }
    case RemoveRouteeActor(actor, name) => {
      log.info(s"FirstGameRoomActor::RemoveRouteeActor ${name}")

      val result = Await.result(for {
        a<-redis.set(s"active:room:${roomName}:userstate:${name}:connected", 0)
      }yield{
        a
      }, timeout)

      if(redis != null)
      {

        val result = Await.result(for {
          a<-redis.expire(s"active:room:${roomName}:userstate:${name}:state", _userExitWaitTime)
          b<-redis.expire(s"active:room:${roomName}:userstate:${name}:score", _userExitWaitTime)
          c<-redis.expire(s"active:room:${roomName}:userstate:${name}:start", _userExitWaitTime)
          d<-redis.expire(s"active:room:${roomName}:userstate:${name}:finish", _userExitWaitTime)
          e<-redis.expire(s"active:room:${roomName}:userstate:${name}:connected", _userExitWaitTime)
          f<-redis.expire(s"active:room:${roomName}:userlist:${name}", _userExitWaitTime)
        }yield{
          a
        }, timeout)

        if(_exitCheckSchduler.exists( key=> key._1 == name)){
          _exitCheckSchduler.get(name).get.cancel()
          _exitCheckSchduler.remove(name)
        }

        val _schedule = context.system.scheduler.schedule(FiniteDuration(_userExitWaitTime, java.util.concurrent.TimeUnit.SECONDS), FiniteDuration(_userExitWaitTime, java.util.concurrent.TimeUnit.SECONDS))({
          for {
            a<-redis.get(s"active:room:${roomName}:userlist:${name}")
          }yield{
            log.info(s"FirstGameRoomActor::RemoveRouteeActor wait resume <${name}>")
            val exist = a getOrElse null
            if (exist == null){
              ActiveClients -= actor.path
              self ! SendServerMessage(s"<${name}> has left the <$roomName> chatroom. exit chat")
              log.info(s"FirstGameRoomActor::RemoveRouteeActor wait resume <${name}> send exit.")
            }
          }
          _exitCheckSchduler.get(name).get.cancel()
        });
        _exitCheckSchduler += (name -> _schedule)

        sender() ! result
      }
    }
    case all @ (clientActorName, message) =>
//    case m @ SendRoomClientMessage(serializer, roomName, clientActorName, message) =>
  }


  def OnTimer(): Unit =
  {
    var init = 0
    var count = 0
    var loop = true
    var userStateTotal = scala.collection.mutable.ListBuffer[GameClientState.GameClientState]()
    if(redis != null) {
      while(loop) {
        val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:userstate:*:state"))
        Await.result(awaitable = for {s <- keys} yield {
          init = s.index
          count += s.data.length
          s.data foreach (value => {
            val userstate = value.asInstanceOf[String]
            val state = Await.result(
              redis.get(s"${userstate}")
              , timeout) getOrElse (ByteString(""))

            val stateResult = GameClientState.fromIndex(state.utf8String.toInt)
            if (stateResult != GameClientState.NONE)
              userStateTotal += stateResult
          })
        }, atMost = timeout)
        if(init == 0)
          loop = false
      }
    }

    if(count == userStateTotal.length && count >= 2)
      log.info(s"[FirstGameRoomActor::OnTimer] all user ready !! ${count} , ${userStateTotal.toString()}")
    else
      log.info(s"[FirstGameRoomActor::OnTimer] some one user does not ready !! ${count} , ${userStateTotal.toString()}")

  }

  def OnRoomDataShareTimer(): Unit = {
    var user : scala.collection.mutable.ListBuffer[String] = getUserList()
    var resultString = s"${FirstGameRoomState.toInt(_roomState)}|${user.size}|"
    user.foreach(desirename => {
      resultString ++= s"${desirename}|"
      val result = Await.result(for {
        a <- redis.get(s"active:room:${roomName}:userstate:${desirename}:state")
        b <- redis.get(s"active:room:${roomName}:userstate:${desirename}:score")
        c <- redis.get(s"active:room:${roomName}:userstate:${desirename}:start")
        d <- redis.get(s"active:room:${roomName}:userstate:${desirename}:finish")
        e <- redis.get(s"active:room:${roomName}:userstate:${desirename}:connected")
      } yield {
        val exist = a getOrElse null
        if (exist != null)
          Some(Tuple5(GameClientState.fromIndex(a.get.utf8String.toInt), b.get.utf8String.toInt, c.get.utf8String.toLong, d.get.utf8String.toLong, e.get.utf8String.toInt))
        else
          Some(null)
      }, timeout)

      if (result.get != null) {
        resultString ++= s"${GameClientState.toInt(result.get._1)}|"
        resultString ++= s"${result.get._2}|"
        resultString ++= s"${result.get._3}|"
        resultString ++= s"${result.get._4}|"
        resultString ++= s"${result.get._5}|"
      }
    })

    ActiveClients.foreach(f => {
      context.actorSelection(f._1) ! SendRoomClientMessage(SERIALIZER.ROW, roomName, s"[RoomRoot::${roomName}]", s"gameroomupdate|${resultString.dropRight(1)}")
    })
  }

  def getUserList():ListBuffer[String] = {
    var result : ListBuffer[String] = ListBuffer()
    var init = 0
    var count = 0
    var loop = true
    if(redis != null) {
      while(loop) {
        val keys = redis.scan(init,Option(100),Option(s"active:room:${roomName}:userlist:*"))
        Await.result(for {s <- keys} yield {
          init = s.index
          count += s.data.length
          s.data foreach(value => {
            val userstate = value.asInstanceOf[String]

            val temp = (userstate splitAt (userstate lastIndexOf ':'))._2.replace(":","")
            result += temp
          })
        }, timeout)
        if(init == 0)
          loop = false
      }
    }
    result
  }
}