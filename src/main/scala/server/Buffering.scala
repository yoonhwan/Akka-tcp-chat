package chatapp.server

import akka.util.ByteString
import java.nio.ByteOrder
import scala.annotation.tailrec

trait Buffering {

  val MAX_PACKET_LEN: Short = 10000

  /**
   * Extracts complete packets of the specified length, preserving remainder
   * data. If there is no complete packet, then we return an empty list. If
   * there are multiple packets available, all packets are extracted, Any remaining data
   * is returned to the caller for later submission
   * @param data A list of the packets extracted from the raw data in order of receipt
   * @return A list of ByteStrings containing extracted packets as well as any remaining buffer data not consumed
   */
  def getPacket(data: ByteString): (List[ByteString], ByteString) = {

    val headerSize = 2
    implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
  
    @tailrec
    def multiPacket(packets: List[ByteString], current: ByteString): (List[ByteString], ByteString) = {
      if (current.length < headerSize) {
        (packets.reverse, current)
      } else {
        val len = current.iterator.getShort
        if (len > MAX_PACKET_LEN || len < 0) throw new RuntimeException(s"Invalid packet length: $len")
        if (current.length < len + headerSize) {
          (packets.reverse, current)
        } else {
          val rem = current drop headerSize // Pop off header
          val (front, back) = rem.splitAt(len) // Front contains a completed packet, back contains the remaining data
          // Pull of the packet and recurse to see if there is another packet available
          multiPacket(front :: packets, back)
        }
      }
    }
    multiPacket(List[ByteString](), data)
  }
}