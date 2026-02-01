package dev.g4s.tess.core

case class ActorUnitOfWork(
    key: ActorKey,
    actorVersion: Long,
    events: Seq[Event],
    startingEventRank: Long
) {
  lazy val endingEventRank: Long = startingEventRank + events.size - 1
  lazy val headEvent: (Option[Event], Option[ActorUnitOfWork]) =
    events.headOption -> {
      if (events.tail.isEmpty) None
      else Some(ActorUnitOfWork(key, actorVersion + 1, events.tail, endingEventRank + 1))
    }
}
