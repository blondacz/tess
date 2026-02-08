package dev.g4s.tess

import dev.g4s.tess.TessConfig.DispatcherFactory
import dev.g4s.tess.coordinator.{Dispatcher, MemorizingDispatcher}
import dev.g4s.tess.core.ActorFactory
import dev.g4s.tess.input.{InputQueues, InputRouter, InputRuntime, InputSettings, InputSource}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all.GenericActorFactory

/** Immutable configuration that delays creation of side-effectful components. */
final case class TessConfig[A](
                                actorFactories: Seq[GenericActorFactory],
                                eventStoreFactory: TessConfig.Factory[EventStore],
                                dispatcherFactory: TessConfig.DispatcherFactory[A],
                                inputSettings: InputSettings = InputSettings()
) {

  def withActorFactories(factories: GenericActorFactory*): TessConfig[A] =
    copy(actorFactories = factories)

  def withEventStore(factory: TessConfig.Factory[EventStore]): TessConfig[A] =
    copy(eventStoreFactory = factory)

  def withDispatcher[B](factory: TessConfig.DispatcherFactory[B]): TessConfig[B] =
    copy(dispatcherFactory = factory)

  /** Configure queue-based ingress. */
  def withInputs(settings: InputSettings): TessConfig[A] =
    copy(inputSettings = settings)

  /** Add a single input source factory (composes with existing settings). */
  def withInputSource(source: InputQueues => InputSource): TessConfig[A] =
    copy(inputSettings = inputSettings.copy(sources = inputSettings.sources :+ source))

  /** Override the DirectInput factory to keep a reference in callers. */
  def withDirectInput(factory: InputQueues => DirectInput): TessConfig[A] =
    copy(inputSettings = inputSettings.copy(directInputFactory = factory))

  /** Instantiate Tess using fresh instances from the configured factories. */
  def build(): Tess[Dispatcher {type ReplayType = A}] = {
    require(actorFactories.nonEmpty, "At least one ActorFactory is required")
    val disp: Dispatcher {type ReplayType = A} = dispatcherFactory.apply()
    new Tess(actorFactories, eventStoreFactory(), disp, inputSettings)
  }
}

object TessConfig {
  type Factory[A] = () => A
  type DispatcherFactory[A] = Factory[Dispatcher {type ReplayType = A}]
}
