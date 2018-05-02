package chatapp.stress


import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.util.{ByteString, Timeout}
import chatapp.client.{ClientActor, SERIALIZER}

import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io
import scala.util.Random
import scala.util.control.Breaks._
/**
  * Created by yoonhwan on 02-4-2018
  */

object OneTimeCode {
  def apply(length: Int = 6) = {
    Random.alphanumeric.take(length).mkString("")
  }
}

case object Finish
case class Make(count:Int)
case class Work()
case object Make
case class Error(actor: ActorRef, msg:String)

class TestActor(receiver:ActorRef, address: InetSocketAddress, name:String) extends Actor{
    import chatapp.client.ClientMessage._
    val clientConnection = context.actorOf(Props(new ClientActor(address, context.system, self)))

  implicit val timeout = Timeout(10 seconds)
    implicit val ec = context.system.dispatcher
    
    def receive:Receive = {
      case ClientConnected => {
        clientConnection ! SendMessage(SERIALIZER.ROW, s"identify|${name}")
        Thread.sleep(2000)
        clientConnection ! SendMessage(SERIALIZER.ROW, "join|stress-room")
      }
      case ClientError(error) => {
        receiver ! Error(self, error)
      }
      case Finish => {
        clientConnection ! PoisonPill
        context stop self
      }
      case Work() =>{
//        val byteSize:Int = 10 //1024*1024*1
//        var message = ByteString(OneTimeCode(byteSize))
//        clientConnection ! SendMessage(SERIALIZER.ROW,s"chat|${message.utf8String}")
        var message :ByteString = ByteString("hgIAAA4AAABEAAAAeQAAAIUAAACJAAAAjQAAAJEAAACZAAAA2QAAABkBAABRAQAAjQEAALoBAADqAQAAGgIAAE4CAAAxAAAAVW5pdHktZWRpdG9yLWQ1MzZiZWYyLTk2ZWQtNDMxZC1iOGU1LWUxNDZkMjA4ZjJmMsQ55VoAAAAA0MkgGMEBAABkAAAAAAB6RAAAAAAAAAAAQAAAAAEAAAABAAAAAAAAADAAAAABAAAADAAAAJJENgD/////BAAAAHRlc3QEAAAAAQAAAAIAAAADAAAABAAAAEAAAAABAAAAAQAAAAAAAAAwAAAAAQAAAAwAAACSRDYA/////wQAAAB0ZXN0BAAAAAAAgD8AAABAAABAQAAAgEA4AAAAAQAAAAEAAAAAAAAAKAAAAAEAAAAMAAAAkkQ2AP////8EAAAAdGVzdAEAAAAAAIA/AAAAQDwAAAABAAAAAQAAAAAAAAAsAAAAAQAAAAwAAACSRDYA/////wQAAAB0ZXN0AQAAAAAAgD8AAABAAACAQC0AAAABAAAAAQAAAAAAAAAdAAAAAQAAAAwAAACSRDYA/////wQAAAB0ZXN0ATAAAAABAAAAAQAAAAAAAAAgAAAAAQAAAAwAAACSRDYA/////wQAAAB0ZXN0AQAAADAAAAABAAAAAQAAAAAAAAAgAAAAAQAAAAwAAACSRDYA/////wQAAAB0ZXN0AACAPzQAAAABAAAAAQAAAAAAAAAkAAAAAQAAAAwAAACSRDYA/////wQAAAB0ZXN0AACAPwAAAEA4AAAAAQAAAAEAAAAAAAAAKAAAAAEAAAAMAAAAkkQ2AP////8EAAAAdGVzdAAAgD8AAABAAABAQA==","UTF-8")
        clientConnection ! SendMessage(SERIALIZER.ZEROF, s"chat|${message.utf8String}")
      }

    }

}

class Manager(address: InetSocketAddress) extends Actor with ActorLogging{
  val ActiveTestActors = HashMap.empty[String, ActorRef]
  val ActiveSchduler = HashMap.empty[String, akka.actor.Cancellable]
  
  
  var totalCount = 1
  def receive:Receive = {
    case Make => {
      if(totalCount > 0)
      {
        val gap = 1000
        var loop = totalCount / gap
        if (loop > 0) {
          for (a <- 0 until gap) {
            CreateTestActor
          }
          totalCount -= gap
        }else {
          var loop = totalCount % gap
          for (a <- 0 until loop) {
            CreateTestActor
          }
          totalCount -= loop
        }

        if(totalCount <= 0)
          totalCount = 0
      }
    }

    case Make(count) =>
    {
      totalCount += count
    }

    case Finish => {
      ActiveSchduler.foreach(f => {
        f._2.cancel()
      })
      ActiveTestActors.foreach(f => {
        f._2 ! Finish
      })
    }
    case Error(actor, msg) => {
      log.info(s"Stress test manager recive : ${actor.path} : ${msg}" )
      ActiveSchduler.get(actor.path.name).get.cancel()
      actor ! Finish

      totalCount += 1
    }
    case _ => {

    }
    
  }

  def CreateTestActor = {
    val name = s"stress-client-${java.util.UUID.randomUUID()}"
    val testActor = context.actorOf(Props(new TestActor(self, address, name)), name)

    val r = scala.util.Random
    r.setSeed(1000L)
    val start = 5.0 + r.nextFloat
    val interval = 200 + r.nextInt(100)
    val cancellable = context.system.scheduler.schedule(Duration(start.toLong,"seconds"), Duration(interval.toLong,"millis"), testActor, Work())
        
    ActiveTestActors += (testActor.path.name -> testActor)
    ActiveSchduler += (testActor.path.name -> cancellable)
  }
}

object StressMain extends App {

      
  val system = ActorSystem("StressMain")
  val Port:Int = system.settings.config.getInt("akka.server.port")
  val Server:String = system.settings.config.getString("akka.server.client-hostname")
  
  val manager = system.actorOf(Props(new Manager(new InetSocketAddress(InetAddress.getByName(Server), Port))))

  system.scheduler.schedule(1 seconds, 5000 millis, manager, Make)

  val bufferedReader = io.Source.stdin.bufferedReader()
  var line: String = null
  while ({line = bufferedReader.readLine; line != null}) { 
      if(loop(line)==false)
        break
  }

  def loop(message: String): Boolean = message match {
    case "q" =>
      manager!Finish
      system.terminate()
      
      false
    case c:String =>
      manager!Make(c.toInt)
      true
    case _ =>
      manager!Finish
      system.terminate()
      
      false
  }
}
