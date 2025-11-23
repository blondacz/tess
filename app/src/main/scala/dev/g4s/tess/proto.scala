package dev.g4s.tess

import dev.g4s.tess.raft.v1.tess.{ActorKey => ProtoActorKey}

object proto {

  def toProtoActorKey(key: ActorKey): ProtoActorKey =
    ProtoActorKey(
      actorType = key.clazz.getName,
      actorId = key.id.asInstanceOf[StandardId].id
    )

  def fromProtoActorKey(pk: ProtoActorKey): ActorKey = {
    val clazz: Class[? <: Actor] = Class.forName(pk.actorType).asInstanceOf[Class[? <: Actor]]
    val id: Id = StandardId(pk.actorId) // parse from pk.actorId
    ActorKey(id, clazz)
  }
}
