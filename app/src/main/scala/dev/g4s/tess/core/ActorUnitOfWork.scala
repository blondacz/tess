package dev.g4s.tess.core

import dev.g4s.tess.core.Message.Reaction
import dev.g4s.tess.core.{CommandMessage, EventMessage, Message, Notification}

case class ActorUnitOfWork(
    key: ActorKey,
    actorVersion: Long,
    reactions: Seq[Reaction],
    startingReactionRank: Long
) {
  // rank now counts all reactions (events, notifications, commands) to preserve ordering for replay/audit
  lazy val endingReactionRank: Long = startingReactionRank + reactions.size - 1

  // convenience to access only events for stateful concerns
  lazy val events: Seq[Event] = reactions.collect { case e: Event => e }

  lazy val headMessage: (Option[Message], Option[ActorUnitOfWork]) =
    reactions.headOption.map(reactionToMessage) -> {
      if (reactions.tail.isEmpty) None
      else Some(ActorUnitOfWork(key, actorVersion + 1, reactions.tail, endingReactionRank + 1))
    }

  private def reactionToMessage(r: Reaction): Message = r match {
    case e: Event         => EventMessage(e)
    case cm: CommandMessage => cm
    case n: Notification  => n
  }
}
