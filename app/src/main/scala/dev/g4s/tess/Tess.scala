package dev.g4s.tess

import dev.g4s.tess.coordinator._
import dev.g4s.tess.core.{ActorFactory, ActorUnitOfWork, Message}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all._

import scala.util.Try

class Tess(actorFactories: Seq[ActorFactory], val eventStore: EventStore, val dispatcher: Dispatcher) {
  private val coordinator: Coordinator = new SimpleCoordinator(eventStore, dispatcher)
  private val messageHandlers = actorFactories.map(af => new MessageHandler(af, coordinator))

  def process(msg: Message): Either[Throwable, Seq[ActorUnitOfWork]] = {
    val lastEventRank = coordinator.start()
    Try(process(msg, List.empty)).toEither match {
      case l @ Left(value) =>
        coordinator.rollback()
        Left(value).withRight[Seq[ActorUnitOfWork]]
      case r @ Right(_) =>
        coordinator.commit()
        Right(dispatcher.replay(lastEventRank))
    }
  }

  // if UOWs are non-empty get first event from first UOW convert it to message, prepend all the UOWs to acc (but the first event) and process the created message
  // if UOWS are empty and acc is not take first event from first UOW prepend rest of the UOW to the ACC process the event (convert to message first)
  // if both UOWS and acc are empty finish
  // discard the UOWs if they don't have any more events
  def process(msg: Message, acc: Seq[ActorUnitOfWork]): Unit = {
    val produced: Seq[ActorUnitOfWork] = messageHandlers.flatMap { mh =>
      if (mh.handle.isDefinedAt(msg)) mh.handle(msg) else Nil
    }

    if (produced.nonEmpty) {
      val res = split(produced.head, produced.tail ++ acc)
      res.foreach { case (nextMsg, newAcc) => process(nextMsg, newAcc) }
    } else if (acc.nonEmpty) {
      val res = split(acc.head, acc.tail)
      res.foreach { case (nextMsg, newAcc) => process(nextMsg, newAcc) }
    }
  }

  private def split(uow: ActorUnitOfWork, acc: Seq[ActorUnitOfWork]): Option[(Message, Seq[ActorUnitOfWork])] =
    uow.headEvent match {
      case (Some(e), nextUow) => Some(e.asMessage -> (nextUow.toSeq ++ acc))
      case (n, _)             => throw new AssertionError(s"Invalid combination event $n for $uow")
    }

}

object Tess {

  /**
    * Backwards-compatible constructor that wires default in-memory components.
    */
  def apply(actorFactories: Seq[ActorFactory]): Tess =
    new Tess(actorFactories, new InMemoryEventStore(), new MemorizingDispatcher())

  /**
    * Entry point for the lazy/wired builder.
    */
  def builder: TessConfig = TessConfig()
}
