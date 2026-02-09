package dev.g4s.tess.input

import dev.g4s.tess.core.Envelope

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

/**
  * Bounded queue for inbound messages. Intended as the handoff point
  * between external sources (Kafka/AMPS/HTTP) and the Tess processor.
  */
final class InputQueue(capacity: Int = 1024) {
  private val queue: BlockingQueue[Envelope] = new LinkedBlockingQueue[Envelope](capacity)

  def offer(msg: Envelope): Boolean = queue.offer(msg)

  def put(msg: Envelope): Unit = queue.put(msg)

  def poll(timeout: Long, unit: TimeUnit): Option[Envelope] = Option(queue.poll(timeout, unit))

  def size: Int = queue.size()
}
