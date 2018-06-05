package chatapp.server

import akka.actor.{ActorSystem, DeadLetter, Props}

import scala.concurrent.{Await, Future}
import scala.io
import scala.util.control.Breaks._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by yoonhwan on 02-4-2018
  */
object ServerMain extends App {
  import client.ClientHandlerMessages._
  import client.ClientHandlerSupervisor._

  val system = ActorSystem("ServerMain")
  val future = Future{
    val redisSupportActor = system.actorOf(RedisSupportActor.props(), "RedisSupportActor")
    var redis = RedisSupportActor.redis.getOrElse(null)
    while (redis == null)
    {
      Thread.sleep(100)
      redis = RedisSupportActor.redis.getOrElse(null)
    }
    system.log.info("RedisSupporter Started")
  }

  Await.result(future, 10 seconds)

  val server = system.actorOf(Props(new ServerActor(system)), "server-actor")
  val serverUdp = system.actorOf(Props(new ServerActorUDP(system)), "server-udp-actor")

  val bufferedReader = io.Source.stdin.bufferedReader()
  

  var line: String = null
  while ({line = bufferedReader.readLine; line != null}) { 
      if(loop(line)==false)
        break
  }

  def loop(message: String): Boolean = message match {
    case ":quit" =>
      system stop server
      system stop serverUdp
      system.terminate()
      false
    case ":clearroom" =>
      server ! ClearAllChatRoom
      true
    case _ =>
      server ! SendServerMessage(message)
      true
      
      
  }

  val deadLettersSubscriber = system.actorOf(Props[EchoActor], name = "dead-letters-subscriber")
  system.eventStream.subscribe(deadLettersSubscriber, classOf[DeadLetter])
}
