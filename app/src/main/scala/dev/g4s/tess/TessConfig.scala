package dev.g4s.tess

import dev.g4s.tess.coordinator.{Dispatcher, MemorizingDispatcher}
import dev.g4s.tess.core.ActorFactory
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}

/** Immutable configuration that delays creation of side-effectful components. */
final case class TessConfig(
    actorFactories: Seq[ActorFactory] = Seq.empty,
    eventStoreFactory: TessConfig.Factory[EventStore] = () => new InMemoryEventStore(),
    dispatcherFactory: TessConfig.Factory[Dispatcher] = () => new MemorizingDispatcher()
) {

  def withActorFactories(factories: ActorFactory*): TessConfig =
    copy(actorFactories = factories)

  def withEventStore(factory: TessConfig.Factory[EventStore]): TessConfig =
    copy(eventStoreFactory = factory)

  def withDispatcher(factory: TessConfig.Factory[Dispatcher]): TessConfig =
    copy(dispatcherFactory = factory)

  /** Instantiate Tess using fresh instances from the configured factories. */
  def build(): Tess = {
    require(actorFactories.nonEmpty, "At least one ActorFactory is required")
    new Tess(actorFactories, eventStoreFactory(), dispatcherFactory())
  }
}

object TessConfig {
  type Factory[A] = () => A
}
