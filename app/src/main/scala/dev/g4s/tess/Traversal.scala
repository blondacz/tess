package dev.g4s.tess

import dev.g4s.tess.coordinator.{Coordinator, Dispatcher, MessageHandler}
import dev.g4s.tess.core.{ActorUnitOfWork, Envelope}

import scala.annotation.tailrec
import scala.util.Try

/** Encapsulates message traversal and reaction replay for Tess. */
final class Traversal[D <: Dispatcher](
    messageHandlers: Seq[MessageHandler],
    coordinator: Coordinator,
    val dispatcher: D
) {
  type Replay = dispatcher.ReplayType

  /** Process a single envelope through all actor handlers, coordinating commit/rollback. */
  def processEnvelope(env: Envelope): Either[Throwable, Replay] = {
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
  private def process(env: Envelope, acc: Seq[ActorUnitOfWork]): Unit = {
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
}
