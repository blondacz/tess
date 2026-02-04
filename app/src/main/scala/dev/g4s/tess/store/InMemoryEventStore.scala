package dev.g4s.tess.store

import dev.g4s.tess.core.{ActorKey, ActorUnitOfWork}

class InMemoryEventStore extends EventStore {
  private val actors = new collection.mutable.HashMap[ActorKey, List[ActorUnitOfWork]]
  private var reactionRank: Option[Long] = None

  override def store(uow: ActorUnitOfWork): Either[Throwable, Unit] = {
    reactionRank = Some(uow.endingReactionRank)
    actors
      .updateWith(uow.key) { maybeUows =>
        Some(maybeUows.fold(List(uow))(_ :+ uow))
      }
      .map(_ => ())
      .toRight(new AssertionError("should not happen in memory"))
  }

  override def load(key: ActorKey): Either[Throwable, List[ActorUnitOfWork]] =
    actors.get(key).orElse(Some(Nil)).toRight(new AssertionError("should not happen in memory"))

  override def lastReactionRank: Option[Long] = reactionRank
}
