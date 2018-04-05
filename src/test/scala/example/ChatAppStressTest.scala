package chatapp.client

import scala.concurrent.duration._
import akka.actor._
import org.scalatest._
import akka.testkit.{ TestProbe, TestKit }

import java.net.InetSocketAddress
import java.net.InetAddress
import akka.io.Tcp._
import chatapp.client.ClientMessage.SendMessage
import akka.util.Timeout
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
      val future = clientConnection ? SendMessage("~identify test01")
      val result = Await.result(future, 1 seconds).asInstanceOf[Success[SendMessage]]

      var a = 10;

      // do loop execution
      val r = new Random(0)
      val buffer = ByteBuffer.allocate(1024 * 4)
      r.nextBytes(buffer.array())
      buffer.limit(buffer.capacity)
      do {
        
        val future1 = clientConnection ? SendMessage(buffer.toString())
        Await.result(future1, 1 seconds).asInstanceOf[Success[SendMessage]]
        
         a = a + 1;
      }
      while( a < 20 )
    }
  }
}