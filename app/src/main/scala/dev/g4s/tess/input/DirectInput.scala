package dev.g4s.tess.input

import dev.g4s.tess.core.Message

/** Direct, synchronous publisher into the input queue; start/stop are no-ops. */
final class DirectInput(private val queue: InputQueue) extends InputSource {
  def offer(msg: Message): Boolean = queue.offer(msg)
  def put(msg: Message): Unit = queue.put(msg)

  override def start(): Unit = () // no-op
  override def stop(): Unit = ()  // no-op
}
