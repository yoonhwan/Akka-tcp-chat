package chatapp.client

import scala.concurrent.duration._
import akka.actor._
import org.scalatest._
import akka.testkit.{ TestProbe, TestKit }

import java.net.InetSocketAddress
import java.net.InetAddress
import akka.io.Tcp._
import chatapp.client.ClientMessage.SendMessage
import akka.util.{Timeout,ByteString}
import java.nio.ByteOrder
import scala.concurrent.Await
import scala.util.{Success,Failure}
import scala.util.Random
import java.nio.ByteBuffer

class TestActor(actor: ActorRef) extends Actor{

    def receive:Receive = {
        case _ =>{
            actor ! _
        }
    }
}

object OneTimeCode {
  def apply(length: Int = 6) = {
    Random.alphanumeric.take(length).mkString("")
  }
}
class ChatAppStressTest
  extends TestKit(ActorSystem("ChatAppStressTest"))
  with WordSpecLike with BeforeAndAfterAll {

  override def afterAll() = {
    system.terminate()
  }

  "The Router" must {
    "routes depending on speed" in {
      import akka.pattern.ask
      import scala.concurrent.duration._
      implicit val timeout = Timeout(3 seconds)
      implicit val ec = system.dispatcher
      
      val endProbe = TestProbe()
      val props = Props(new TestActor(endProbe.ref))

      val Port:Int = system.settings.config.getInt("akka.server.port")
      val Server:String = system.settings.config.getString("akka.server.hostname")
      val clientConnection = system.actorOf(Props(new ClientActor(new InetSocketAddress(InetAddress.getByName(Server), Port), system)))
      expectNoMsg(2 seconds)
      clientConnection ! SendMessage("~identify test01")
      
      var a = 10;

      // println(ByteString("abc"))
      // println(ByteString("가나다"))
      // println(ByteString("abc").length)
      // println(ByteString("가나다").length)
      // println(ByteString("abc").utf8String)
      // println(ByteString("가나다").utf8String)
      // println(ByteString("abc").utf8String.length)
      // println(ByteString("가나다").utf8String.length)

      implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
      val msg = ByteString("스트레스","UTF-8")
      val current = ByteString.newBuilder
      .putInt(msg.length.toInt)
      .result() ++ msg

      val headerSize = 4
      val len = current.iterator.getInt
      val rem = current drop headerSize 
      val (front, back) = rem.splitAt(len) 
      println(len)
      println(front + " : "  + back)

      do {
        var message = ByteString(OneTimeCode(1024*1024*1))
        clientConnection ! SendMessage(message.utf8String)
        a = a + 1;
      }
      while( true )
    }
  }
}