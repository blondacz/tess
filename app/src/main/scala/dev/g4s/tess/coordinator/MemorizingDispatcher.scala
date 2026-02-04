package dev.g4s.tess.coordinator

import dev.g4s.tess.core.ActorUnitOfWork

class MemorizingDispatcher extends Dispatcher {
  private val dispatched = new collection.mutable.ArrayBuffer[ActorUnitOfWork]
  private var commitedUptTo = 0L

  override def dispatch(unitOfWork: ActorUnitOfWork): Unit =
    dispatched += unitOfWork

  def replay(from: Long): List[ActorUnitOfWork] =
    dispatched.filter(_.endingReactionRank >= from).toList

  def commit(reactionRank: Long): Unit = commitedUptTo = reactionRank

  def rollback(): Unit = dispatched.dropWhile(_.endingReactionRank > commitedUptTo)
}
