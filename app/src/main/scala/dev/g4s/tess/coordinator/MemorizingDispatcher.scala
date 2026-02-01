package dev.g4s.tess.coordinator

import dev.g4s.tess.core.ActorUnitOfWork

class MemorizingDispatcher extends Dispatcher {
  private val dispatched = new collection.mutable.ArrayBuffer[ActorUnitOfWork]
  private var commitedUptTo = 0L

  override def dispatch(unitOfWork: ActorUnitOfWork): Unit =
    dispatched += unitOfWork

  def replay(from: Long): List[ActorUnitOfWork] =
    dispatched.filter(_.endingEventRank >= from).toList

  def commit(eventRank: Long): Unit = commitedUptTo = eventRank

  def rollback(): Unit = dispatched.dropWhile(_.endingEventRank > commitedUptTo)
}
