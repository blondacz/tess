package dev.g4s.tess.coordinator

import dev.g4s.tess.core.ActorUnitOfWork

trait Dispatcher {
  type ReplayType
  def dispatch(unitOfWork: ActorUnitOfWork): Unit
  def replay(from: Long): ReplayType
  def commit(reactionRank: Long): Unit
  def rollback(): Unit
}
