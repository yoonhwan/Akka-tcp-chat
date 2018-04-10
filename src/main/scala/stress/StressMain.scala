package chatapp.stress


import java.net.InetSocketAddress
import java.net.InetAddress
import akka.actor.{ActorSystem, Props, PoisonPill, Actor, ActorRef}
import akka.io.Tcp._
import chatapp.client.ClientActor
import chatapp.client.ClientMessage.SendMessage
import scala.io
import scala.util.control.Breaks._
import scala.util.Random
import java.nio.ByteBuffer
import scala.concurrent.duration._
import akka.util.{Timeout,ByteString}

import scala.concurrent.{Await,Future,Promise}
import scala.util.{Success,Failure}
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import chatapp.server.Util
import scala.collection.mutable.HashMap
/**
  * Created by Niels Bokmans on 30-3-2016.
  */

object OneTimeCode {
  def apply(length: Int = 6) = {
    Random.alphanumeric.take(length).mkString("")
  }
}

case object Finish
case object Start
case class Make(count:Int)
case class Work()
class TestActor(address: InetSocketAddress, name:String) extends Actor{
    val clientConnection = context.actorOf(Props(new ClientActor(address, context.system, true)))
      
    implicit val timeout = Timeout(10 seconds)
    implicit val ec = context.system.dispatcher
    
    def receive:Receive = {
      case Start => {
        Thread.sleep(1000)
        Await result (clientConnection ? SendMessage(s":identify ${name}"), 10 seconds)
        Thread.sleep(1000)
        Await result (clientConnection ? SendMessage(":join stress-room"), 10 seconds)
        Thread.sleep(1000)
      }
      case Finish => {
        clientConnection ! PoisonPill
    
      }
      case Work() =>{
        val bufferedReader = io.Source.stdin.bufferedReader()
        val byteSize:Int = 100 //1024*1024*1
        var message = ByteString(OneTimeCode(byteSize))
        clientConnection ! SendMessage(message.utf8String)
      }

    }

}

class Manager(address: InetSocketAddress) extends Actor {
  val ActiveTestActors = HashMap.empty[String, ActorRef]
  val ActiveSchduler = HashMap.empty[String, akka.actor.Cancellable]
  
  

  def receive:Receive = {
    case Make(count) =>
    {
      for (a <- 0 until count) {
        CreateTestActor
      }
    }
    case Finish => {
      ActiveSchduler.foreach(f => {
        f._2.cancel()
      })
      ActiveTestActors.foreach(f => {
        f._2 ! Finish
      })
    }
    case _ => {

    }
    
  }

  def CreateTestActor = {
    val name = s"stress-client-${java.util.UUID.randomUUID()}"
    val testActor = context.actorOf(Props(new TestActor(address, name)))
    testActor ! Start
    val cancellable = context.system.scheduler.schedule(5 seconds, 200 millis, testActor, Work())
        
    ActiveTestActors += (name -> testActor)
    ActiveSchduler += (name -> cancellable)
  }
}

object StressMain extends App {

      
  val system = ActorSystem("StressMain")
  val Port:Int = system.settings.config.getInt("akka.server.port")
  val Server:String = system.settings.config.getString("akka.server.hostname")
  
  val manager = system.actorOf(Props(new Manager(new InetSocketAddress(InetAddress.getByName(Server), Port))))

  val bufferedReader = io.Source.stdin.bufferedReader()
  var line: String = null
  while ({line = bufferedReader.readLine; line != null}) { 
      if(loop(line)==false)
        break
  }

  def loop(message: String): Boolean = message match {
    case c:String =>
      manager!Make(c.toInt)
      true
    case _ =>
      manager!Finish
      system.terminate()
      
      false
  }
}
