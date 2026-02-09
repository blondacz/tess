package dev.g4s.tess.input

import dev.g4s.tess.core.{Envelope, Message, TraceContext}

/** Direct, synchronous publisher into the input queue; start/stop are no-ops. */
final class DirectInput(private val queue: InputQueue) extends InputSource {
  def offer(msg: Message, trace: TraceContext = TraceContext.empty): Boolean = queue.offer(Envelope(msg, trace))
  def put(msg: Message, trace: TraceContext = TraceContext.empty): Unit = queue.put(Envelope(msg, trace))

  override def start(): Unit = () // no-op
  override def stop(): Unit = ()  // no-op
}
