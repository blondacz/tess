package dev.g4s.tess

import dev.g4s.tess.coordinator._
import dev.g4s.tess.core.{ActorUnitOfWork, Envelope, Message}
import dev.g4s.tess.input.{DirectInput, InputQueues, InputRuntime, InputSettings, InputRouter}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all._

import scala.annotation.tailrec
import scala.util.Try

class Tess[D <: Dispatcher](
    actorFactories: Seq[GenericActorFactory],
    val eventStore: EventStore,
    val dispatcher: D,
    inputSettings: InputSettings
) {
  private val coordinator: Coordinator = new SimpleCoordinator(eventStore, dispatcher)
  private val messageHandlers = actorFactories.map(af => new MessageHandler(af, coordinator))
  private val queues: InputQueues = InputQueues(inputSettings.adminCapacity, inputSettings.busCapacity, inputSettings.inputCapacity)
  private val router = new InputRouter[dispatcher.ReplayType](
    processEnvelope,
    queues.admin,
    queues.bus,
    queues.input,
    inputSettings.pollTimeout,
    inputSettings.busBurst
  )
  private val sources = inputSettings.sources.map(_(queues)) 
  private val runtime: InputRuntime[dispatcher.ReplayType] = new InputRuntime(
    queues.admin,
    queues.bus,
    queues.input,
    sources,
    router
  )

  private def processEnvelope(env: Envelope): Either[Throwable, dispatcher.ReplayType] = {
    val lastReactionRank = coordinator.start()
    Try(process(env, List.empty)).toEither match {
      case l @ Left(value) =>
        coordinator.rollback()
        Left(value).withRight[dispatcher.ReplayType]
      case r @ Right(_) =>
        coordinator.commit()
        Right(dispatcher.replay(lastReactionRank))
    }
  }

  @tailrec
  private final def process(env: Envelope, acc: Seq[ActorUnitOfWork]): Unit = {
    val producedUows: Seq[ActorUnitOfWork] = messageHandlers.flatMap { mh =>
      if (mh.handle.isDefinedAt(env)) mh.handle(env) else Nil
    }

    val nextAcc = producedUows ++ acc

    if (nextAcc.nonEmpty) {
      split(nextAcc.head, nextAcc.tail) match {
        case None => ()
        case Some(nextEnv, newAcc) => process(nextEnv, newAcc)
      }
    }
  }

  private def split(uow: ActorUnitOfWork, acc: Seq[ActorUnitOfWork]): Option[(Envelope, Seq[ActorUnitOfWork])] =
    uow.headEnvelope match {
      case (Some(env), nextUow) => Some(env -> (nextUow.toSeq ++ acc))
      case (n, _)               => throw new AssertionError(s"Invalid combination event $n for $uow")
    }


  def startInputs(): Unit = runtime.start()
  def stopInputs(): Unit = runtime.stop()
}

object Tess {

  /**
    * Backwards-compatible constructor that wires default in-memory components and direct input.
    */
  def apply(actorFactories: Seq[GenericActorFactory]): Tess[?] = {
    TessConfig(actorFactories, () => new InMemoryEventStore(), () => new MemorizingDispatcher()).build()
  }

  /**
    * Entry point for the lazy/wired builder.
    */
  def builder: TessConfig[List[ActorUnitOfWork]] =
    TessConfig(Nil, () => new InMemoryEventStore(), () => new MemorizingDispatcher())
}
