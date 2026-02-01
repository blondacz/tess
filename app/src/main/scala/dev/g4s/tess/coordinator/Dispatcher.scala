package dev.g4s.tess.coordinator

import dev.g4s.tess.core.ActorUnitOfWork

trait Dispatcher {
  def dispatch(unitOfWork: ActorUnitOfWork): Unit
  def replay(from: Long): List[ActorUnitOfWork]
  def commit(eventRank: Long): Unit
  def rollback(): Unit
}
