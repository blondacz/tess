package dev.g4s.tess

import dev.g4s.tess.coordinator._
import dev.g4s.tess.core.{ActorUnitOfWork, Message}
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
    processMessage,
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

  private def processMessage(msg: Message): Either[Throwable, dispatcher.ReplayType] = {
    val lastReactionRank = coordinator.start()
    Try(process(msg, List.empty)).toEither match {
      case l @ Left(value) =>
        coordinator.rollback()
        Left(value).withRight[dispatcher.ReplayType]
      case r @ Right(_) =>
        coordinator.commit()
        Right(dispatcher.replay(lastReactionRank))
    }
  }

  @tailrec
  private final def process(msg: Message, acc: Seq[ActorUnitOfWork]): Unit = {
    val producedUows: Seq[ActorUnitOfWork] = messageHandlers.flatMap { mh =>
      if (mh.handle.isDefinedAt(msg)) mh.handle(msg) else Nil
    }

    val nextAcc = producedUows ++ acc

    if (nextAcc.nonEmpty) {
      split(nextAcc.head, nextAcc.tail) match {
        case None => ()
        case Some(nextMsg, newAcc) => process(nextMsg, newAcc)
      }
    }
  }

  private def split(uow: ActorUnitOfWork, acc: Seq[ActorUnitOfWork]): Option[(Message, Seq[ActorUnitOfWork])] =
    uow.headMessage match {
      case (Some(msg), nextUow) => Some(msg -> (nextUow.toSeq ++ acc))
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
