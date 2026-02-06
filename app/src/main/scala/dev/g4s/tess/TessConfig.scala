package dev.g4s.tess

import dev.g4s.tess.TessConfig.DispatcherFactory
import dev.g4s.tess.coordinator.{Dispatcher, MemorizingDispatcher}
import dev.g4s.tess.core.ActorFactory
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all.GenericActorFactory
import org.checkerframework.checker.units.qual.A

/** Immutable configuration that delays creation of side-effectful components. */
final case class TessConfig[A](
                                actorFactories: Seq[GenericActorFactory],
                                eventStoreFactory: TessConfig.Factory[EventStore],
                                dispatcherFactory: TessConfig.DispatcherFactory[A]
) {

  def withActorFactories(factories: GenericActorFactory*): TessConfig[A] =
    copy(actorFactories = factories)

  def withEventStore(factory: TessConfig.Factory[EventStore]): TessConfig[A] =
    copy(eventStoreFactory = factory)

  def withDispatcher[B](factory: TessConfig.DispatcherFactory[B]): TessConfig[B] =
    copy(dispatcherFactory = factory)

  /** Instantiate Tess using fresh instances from the configured factories. */
  def build(): Tess[Dispatcher {type ReplayType = A}] = {
    require(actorFactories.nonEmpty, "At least one ActorFactory is required")
    val disp: Dispatcher {type ReplayType = A} = dispatcherFactory.apply()
    new Tess(actorFactories, eventStoreFactory(), disp)
  }
}

object TessConfig {
  type Factory[A] = () => A
  type DispatcherFactory[A] = Factory[Dispatcher {type ReplayType = A}]
}
