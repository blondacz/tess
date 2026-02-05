package dev.g4s.tess

import dev.g4s.tess.coordinator._
import dev.g4s.tess.core.{ActorFactory, ActorUnitOfWork, Message}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all._

import scala.annotation.tailrec
import scala.util.Try

class Tess(actorFactories: Seq[ActorFactory], val eventStore: EventStore, val dispatcher: Dispatcher) {
  private val coordinator: Coordinator = new SimpleCoordinator(eventStore, dispatcher)
  private val messageHandlers = actorFactories.map(af => new MessageHandler(af, coordinator))

  def process(msg: Message): Either[Throwable, Seq[ActorUnitOfWork]] = {
    val lastReactionRank = coordinator.start()
    Try(process(msg, List.empty)).toEither match {
      case l @ Left(value) =>
        coordinator.rollback()
        Left(value).withRight[Seq[ActorUnitOfWork]]
      case r @ Right(_) =>
        coordinator.commit()
        Right(dispatcher.replay(lastReactionRank))
    }
  }

  // if UOWs are non-empty get first reaction from first UOW convert it to message, prepend all the UOWs to acc (but the first reaction) and process the created message
  // if UOWS are empty and acc is not take first reaction from first UOW prepend rest of the UOW to the ACC process the reaction (convert to message first)
  // if both UOWS and acc are empty finish
  // discard the UOWs if they don't have any more reactions
  @tailrec
  private final def process(msg: Message, acc: Seq[ActorUnitOfWork]): Unit = {
    val producedUows: Seq[ActorUnitOfWork] = messageHandlers.flatMap { mh =>
      if (mh.handle.isDefinedAt(msg)) mh.handle(msg) else Nil
    }

    val nextAcc = producedUows ++ acc

    if (nextAcc.nonEmpty) {
      val res = split(nextAcc.head, nextAcc.tail)
      res match {
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
