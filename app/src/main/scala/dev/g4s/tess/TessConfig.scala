package dev.g4s.tess

import dev.g4s.tess.TessConfig.{DispatcherFactory, Resource}
import dev.g4s.tess.coordinator.{Dispatcher, MemorizingDispatcher}
import dev.g4s.tess.core.ActorFactory
import dev.g4s.tess.input.{InputQueues, InputRouter, InputRuntime, InputSettings, InputSource}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all.GenericActorFactory

/** Immutable configuration that delays creation of side-effectful components. */
final case class TessConfig[A](
                                actorFactories: Seq[GenericActorFactory],
                                eventStoreResource: Resource[EventStore],
                                dispatcherResource: Resource[Dispatcher {type ReplayType = A}],
                                inputSettings: InputSettings = InputSettings()
) {

  def withActorFactories(factories: GenericActorFactory*): TessConfig[A] =
    copy(actorFactories = factories)

  def withEventStore(resource: Resource[? <: EventStore]): TessConfig[A] =
    copy(eventStoreResource = resource.upcast[EventStore])

  def withEventStore(factory: TessConfig.Factory[EventStore]): TessConfig[A] =
    copy(eventStoreResource = TessConfig.Resource(factory))

  def withDispatcher[B](resource: TessConfig.Resource[? <: Dispatcher {type ReplayType = B}]): TessConfig[B] =
    copy(dispatcherResource = resource.upcast[Dispatcher {type ReplayType = B}])

  def withDispatcher[B](factory: TessConfig.DispatcherFactory[B]): TessConfig[B] =
    copy(dispatcherResource = TessConfig.Resource(factory))

  /** Configure queue-based ingress. */
  def withInputs(settings: InputSettings): TessConfig[A] =
    copy(inputSettings = settings)

  /** Add a single input source factory (composes with existing settings). */
  def withInputSource(source: InputQueues => InputSource): TessConfig[A] =
    copy(inputSettings = inputSettings.copy(sources = inputSettings.sources :+ source))

  /** Override the DirectInput factory to keep a reference in callers. */
  def withDirectInput(factory: InputQueues => DirectInput): TessConfig[A] =
    copy(inputSettings = inputSettings.copy(directInputFactory = factory))

  /** Instantiate Tess using fresh instances with managed acquire/release semantics. */
  def build(): Tess[Dispatcher {type ReplayType = A}] = {
    require(actorFactories.nonEmpty, "At least one ActorFactory is required")
    val es = eventStoreResource.acquire()
    val disp: Dispatcher {type ReplayType = A} = dispatcherResource.acquire()
    val release = () => {
      dispatcherResource.release(disp)
      eventStoreResource.release(es)
    }
    new Tess(actorFactories, es, disp, inputSettings, release)
  }
}

object TessConfig {
  type Factory[A] = () => A
  type DispatcherFactory[A] = Factory[Dispatcher {type ReplayType = A}]

  /** Lightweight alternative to cats.Resource for wiring acquisition + release semantics. */
  final case class Resource[A](acquire: Factory[A], release: A => Unit = (_: A) => ()) {
    def upcast[B >: A]: Resource[B] = Resource(() => acquire(), (b: B) => release(b.asInstanceOf[A]))
  }

  object Resource {
    def apply[A](factory: Factory[A]): Resource[A] = new Resource(factory, (_: A) => ())
    def closing[A <: AutoCloseable](factory: Factory[A]): Resource[A] = new Resource(factory, _.close())
  }
}
