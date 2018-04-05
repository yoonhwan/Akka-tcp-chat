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
import scala.concurrent.Await
import scala.util.{ Success, Failure }
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

      // do loop execution
      do {
        println(OneTimeCode(10))
        clientConnection ! SendMessage(OneTimeCode(1024))
        a = a + 1;
      }
      while( true )
    }
  }
}