package dev.g4s.tess.coordinator

import dev.g4s.tess.core.{ActorUnitOfWork, Notification}

class MemorizingDispatcher extends Dispatcher {
  override type ReplayType = List[ActorUnitOfWork]
  private val dispatched = new collection.mutable.ArrayBuffer[ActorUnitOfWork]
  private var commitedUptTo = 0L

  override def dispatch(unitOfWork: ActorUnitOfWork): Unit =
    dispatched += unitOfWork

  def replay(from: Long): List[ActorUnitOfWork] =
    dispatched.filter(_.endingReactionRank >= from).toList

  def commit(reactionRank: Long): Unit = commitedUptTo = reactionRank

  def rollback(): Unit = dispatched.dropWhile(_.endingReactionRank > commitedUptTo)
}


class NotificationDispatcher(d: MemorizingDispatcher) extends Dispatcher {
  override type ReplayType = Seq[Notification]

  override def dispatch(unitOfWork: ActorUnitOfWork): Unit = d.dispatch(unitOfWork)

  override def replay(from: Long): Seq[Notification] = d.replay(from).flatMap(uow => uow.reactions.collect { case n: Notification => n }) 

  override def commit(reactionRank: Long): Unit = d.commit(reactionRank)

  override def rollback(): Unit = d.rollback()
}
