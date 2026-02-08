package dev.g4s.tess.input

import dev.g4s.tess.core.Message

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

/**
  * Bounded queue for inbound messages. Intended as the handoff point
  * between external sources (Kafka/AMPS/HTTP) and the Tess processor.
  */
final class InputQueue(capacity: Int = 1024) {
  private val queue: BlockingQueue[Message] = new LinkedBlockingQueue[Message](capacity)

  def offer(msg: Message): Boolean = queue.offer(msg)

  def put(msg: Message): Unit = queue.put(msg)

  def poll(timeout: Long, unit: TimeUnit): Option[Message] = Option(queue.poll(timeout, unit))

  def size: Int = queue.size()
}
