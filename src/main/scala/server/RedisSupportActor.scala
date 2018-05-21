package chatapp.server


import akka.actor.{Actor, ActorIdentity, ActorRef, Props}
import akka.event.Logging
import akka.io.Tcp.Connected
import akka.util.Timeout
import redis._
import redis.api.pubsub.{Message, PMessage}
import redis.commands.TransactionBuilder
import redis.protocol.{Bulk, MultiBulk, Status}

import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object RedisSupportActor {
  def props(): Props = Props(classOf[RedisSupportActor])
  case class Connect()
  case class ConnectManual(var host: String,
                           var port: Int)
  case class Disconnect()

  case class PubSubCreate()
  case class Subscribe(callback: scala.Function1[String, scala.Unit], channels : String*)
  case class PSubscribe(callback: scala.Function1[String, scala.Unit], channels : String*)
  case class UnSubscribe(channels : String*)
  case class PunSubscribe(channels : String*)
  case class Publish(channel:String, message: String)

  case class RedisFunction1()
  case class RedisFunction2()
  case class RedisFunction3(operations: (TransactionBuilder) => Unit)

  var redis : Option[RedisClientMasterSlaves] = None
  var redisPubSub: Option[RedisPubSub] = None

  var inst : ActorRef = null
}

class RedisSupportActor extends Actor
{
  import RedisSupportActor._
  val timeOut = 5 second
  implicit val t = Timeout(timeOut)
  val log = Logging(context.system, this)
  val Port:Int = context.system.settings.config.getInt("akka.redis.port")
  val Server:String = context.system.settings.config.getString("akka.redis.hostname")

  RedisSupportActor.inst = self
//  val remoteConfig = context.system.settings.config.getConfig("akka").getConfig("remote").getConfig("netty.tcp")
//  val actorHost = remoteConfig.getString("hostname")
//  val actorPort = remoteConfig.getInt("port")
//  val workerName = self.path.name
//  val actorPath = "akka.tcp://" + context.system.name + "@" + actorHost + ":" + actorPort + "/user/" + workerName
//  log.info(actorPath)
//  //remote actorselection
//  val tet = context.system.actorSelection(actorPath)
//  tet ! "124124124"

  val messageCallbacks = HashMap.empty[String, scala.Function1[String, scala.Unit]]
  val pmessageCallbacks = HashMap.empty[String, scala.Function1[String, scala.Unit]]

  ConnectRedis(Server,Port)
  def RedisStop(redisClientMasterSlaves: RedisClientMasterSlaves): Unit =
  {
    redisClientMasterSlaves.masterClient.stop()
    redisClientMasterSlaves.slavesClients.stop()

    if(redisPubSub.getOrElse(null) != null) {
      redisPubSub.get stop()
      redisPubSub = Option(null)
    }

    messageCallbacks.clear()
    pmessageCallbacks.clear()
  }

  override def receive: Receive = {
    case ActorIdentity(selection, ref) => {

    }
    case Connect() => {
      ConnectRedis(Server, Port)
    }
    case ConnectManual(host, port) => {
      ConnectRedis(host, port)
    }
    case Disconnect() => {
      redis match {
        case Some(n) => {
          RedisStop(n)
        }
        case None =>
      }
    }
    case PubSubCreate() => {
      sender ! createPubsub()
//      redisPubSub.stop()
    }

    case Subscribe(callback, channels) => {
      createPubsub()
      messageCallbacks.put(channels,callback)
      redisPubSub.get.subscribe(channels)
    }
    case PSubscribe(callback, channels) => {
      createPubsub()
      pmessageCallbacks.put(channels,callback)
      redisPubSub.get.psubscribe(channels)
    }

    case UnSubscribe(channels) => {
      createPubsub()
      messageCallbacks.remove(channels)
      redisPubSub.get.subscribe(channels)
    }
    case PunSubscribe(channels) => {
      createPubsub()
      pmessageCallbacks.remove(channels)
      redisPubSub.get.psubscribe(channels)
    }

    case Publish(channel:String, message: String) =>{
      redis match {
        case Some(n) => {
          n.publish(channel, message)
        }
        case err =>
          context.system.actorSelection(sender.path) ! Failure(new Exception(s"connect error : ${err}"))
      }
    }

    case RedisFunction1() => {
      RedisFunction1()
    }
    case RedisFunction2() => {
      RedisFunction2()
    }
    case RedisFunction3(operations) => {
      RedisFunction3(operations)
    }
    case Connected(host, host2) => {
      log.info(s"Connected : ${host}")
    }
    case error =>{
      log.error(s"error : ${error}")
    }
  }

  def ConnectRedis(host: String = "localhost", port: Int = 6379) = {
    implicit  val akkaSystem = context.system

    redis match {
      case Some(n) => {
        RedisStop(n)

        redis = Option(RedisClientMasterSlaves(RedisServer(host = host, port = port), Seq(RedisServer(host=host,port=port))))
      }
      case None =>
        redis = Option(RedisClientMasterSlaves(RedisServer(host = host, port = port), Seq(RedisServer(host=host,port=port))))
    }

    redis match {
      case Some(n) => {
        val futurePong = n.ping()
        log.info("Ping sent!")
        futurePong.map(pong => {
          log.info(s"Redis replied with a $pong")
        })
        Await.result(futurePong, timeOut)
        context.system.actorSelection(sender.path) ! Success("connect success")
      }
      case err =>
        context.system.actorSelection(sender.path) ! Failure(new Exception(s"connect error : ${err}"))
    }

    createPubsub()
  }

  def createPubsub(): Option[RedisPubSub]= {
    if(redisPubSub.getOrElse(null) == null) {
      redisPubSub = Option(RedisPubSub(
        Server,
        Port,
        channels = Seq(),
        patterns = Seq(),
        onMessage = onMessage,
        onPMessage = onPMessage
      ))
    }
    redisPubSub
  }
  def onMessage(message: Message) {
    if(messageCallbacks.size <= 0)
      log.info(s"message received: ${message.data.utf8String}")

    Future {
      messageCallbacks.foreach(f => {
        if (f._1.compare(message.channel) == 0)
          f._2(message.data.utf8String)
      })
    }
  }

  def onPMessage(pmessage: PMessage) {
    if(pmessageCallbacks.size <= 0)
      log.info(s"pattern message received: ${pmessage.data.utf8String}")
    Future {
      pmessageCallbacks.foreach(f => {
        if (f._1.compare(pmessage.patternMatched) == 0)
          f._2(pmessage.data.utf8String)
      })
    }
  }

  def RedisFunction1() : Unit = {
    redis match {
      case Some(n) => {
        val redisTransaction = n.multi(redis => {
          redis.set("a", "abc", pxMilliseconds = Option(1000))
          redis.get("a")
        })
        val exec = redisTransaction.exec()
        Await.result(exec, timeOut) match {
          case MultiBulk(Some(Vector(status, Bulk(Some(second))))) => {
            log.info(s"RedisFunction1 result ${status.asInstanceOf[Status].toBoolean}, ${second.utf8String}")
            context.system.actorSelection(sender.path) ! Success(status.asInstanceOf[Status].toBoolean)
          }

          case _ => context.system.actorSelection(sender.path) ! Failure(new Exception("RedisFunction1 fail"))
        }
      }
      case None => context.system.actorSelection(sender.path) ! Failure(new Exception("Supporter doesn't connect any redis server. connection first"))
    }
  }

  def RedisFunction2() : Unit = {
    redis match {
      case Some(n) => {
        val redisTransaction = n.multi(redis => {
          redis.get("a")
        })
        val exec = redisTransaction.exec()
        Await.result(exec, timeOut) match {
          case MultiBulk(Some(Vector(Bulk(Some(data))))) => {
            log.info(s"RedisFunction2 result ${data.utf8String}")
            context.system.actorSelection(sender.path) ! Success(data.utf8String)
          }
          case _ => context.system.actorSelection(sender.path) ! Failure(new Exception("RedisFunction2 fail"))
        }
      }
      case None => {
        context.system.actorSelection(sender.path) ! Failure(new Exception("Supporter doesn't connect any redis server. connection first"))
      }
    }
  }

  def RedisFunction3(operations: (TransactionBuilder) => Unit) : Unit = {
    redis match {
      case Some(n) => {
        val redisTransaction = n.multi(operations)
        val exec = redisTransaction.exec()
        Await.result(exec, timeOut) match {
          case MultiBulk(Some(vector)) => {
            log.info(s"RedisFunction3 result ${vector.length} ${vector}")

            val result = vector.map(item => {
              item.toString
            })
            context.system.actorSelection(sender.path) ! result
          }
          case _ => context.system.actorSelection(sender.path) ! Failure(new Exception("RedisFunction3 fail"))
        }
      }
      case None => {
        context.system.actorSelection(sender.path) ! Failure(new Exception("Supporter doesn't connect any redis server. connection first"))
      }
    }
  }
}

