package dev.g4s.tess.input

import scala.concurrent.duration.*

/** Configuration for queue-based ingress. */
final case class InputSettings(
    sources: Seq[InputQueues => InputSource] = Seq.empty,
    directInputFactory: InputQueues => DirectInput = qs => new DirectInput(qs.input),
    adminCapacity: Int = 128,
    busCapacity: Int = 256,
    inputCapacity: Int = 1024,
    pollTimeout: FiniteDuration = 200.millis,
    busBurst: Int = 4
)

/** Helper factory for the three queues. */
final case class InputQueues(admin: InputQueue, bus: InputQueue, input: InputQueue)
object InputQueues {
  def apply(adminCap: Int, busCap: Int, inputCap: Int): InputQueues =
    InputQueues(new InputQueue(adminCap), new InputQueue(busCap), new InputQueue(inputCap))
}
