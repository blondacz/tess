package dev.g4s.tess

import dev.g4s.tess.TessConfig.Resource
import dev.g4s.tess.coordinator._
import dev.g4s.tess.core.{ActorUnitOfWork, Envelope, Message}
import dev.g4s.tess.input.{DirectInput, InputQueues, InputRuntime, InputSettings, InputRouter}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all._

class Tess[D <: Dispatcher](
    actorFactories: Seq[GenericActorFactory],
    val eventStore: EventStore,
    val dispatcher: D,
    inputSettings: InputSettings,
    private val releaseResources: () => Unit = () => ()
) {
  private val coordinator: Coordinator = new SimpleCoordinator(eventStore, dispatcher)
  private val messageHandlers = actorFactories.map(af => new MessageHandler(af, coordinator))
  private val reactor = new TessReactor[D](messageHandlers, coordinator, dispatcher)
  private val queues: InputQueues = InputQueues(inputSettings.adminCapacity, inputSettings.busCapacity, inputSettings.inputCapacity)
  private val router = new InputRouter[reactor.Replay](
    reactor.processEnvelope,
    queues.admin,
    queues.bus,
    queues.input,
    inputSettings.pollTimeout,
    inputSettings.busBurst
  )
  private val sources = inputSettings.sources.map(_(queues)) 
  private val runtime: InputRuntime[reactor.Replay] = new InputRuntime(
    queues.admin,
    queues.bus,
    queues.input,
    sources,
    router
  )

  def startInputs(): Unit = runtime.start()
  def stopInputs(): Unit = runtime.stop()

  /** Acquire/start lifecycle: returns this for fluent usage. */
  def start(): Tess[D] = {
    startInputs()
    this
  }

  /** Stop inputs and release associated resources. Idempotent. */
  def stop(): Unit = {
    stopInputs()
    releaseResources()
  }

  /** Alias for stop to integrate with try-with-resources style. */
  def close(): Unit = stop()
}

object Tess {

  /**
    * Backwards-compatible constructor that wires default in-memory components and direct input.
    */
  def apply(actorFactories: Seq[GenericActorFactory]): Tess[Dispatcher {type ReplayType = List[ActorUnitOfWork]}] =
    TessConfig(
      actorFactories,
      Resource(() => new InMemoryEventStore()),
      Resource(() => new MemorizingDispatcher())
    ).build()

  /**
    * Entry point for the lazy/wired builder.
    */
  def builder: TessConfig[List[ActorUnitOfWork]] =
    TessConfig(
      Nil,
      Resource(() => new InMemoryEventStore()),
      Resource(() => new MemorizingDispatcher())
    )
}
