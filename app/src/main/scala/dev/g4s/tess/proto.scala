package dev.g4s.tess

import com.google.protobuf.ByteString
import dev.g4s.tess.core.{Actor, ActorKey, ActorUnitOfWork, Event, Id}
import dev.g4s.tess.domain.StandardId
import dev.g4s.tess.raft.v1.tess.{ActorKey => ProtoActorKey, ActorUnitOfWork => ProtoActorUnitOfWork, EventEnvelope => ProtoEventEnvelope}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

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

  def toProtoEvent(e: Event): ProtoEventEnvelope =
    ProtoEventEnvelope(
      eventType = e.getClass.getName,
      payload = ByteString.copyFrom(serialize(e))
    )

  def fromProtoEvent(pe: ProtoEventEnvelope): Event =
    deserialize(pe.payload.toByteArray)

  def toProtoActorUnitOfWork(uow: ActorUnitOfWork): ProtoActorUnitOfWork =
    ProtoActorUnitOfWork(
      key = toProtoActorKey(uow.key),
      actorVersion = uow.actorVersion,
      startingEventRank = uow.startingEventRank,
      events = uow.events.map(toProtoEvent)
    )

  def fromProtoActorUnitOfWork(uow: ProtoActorUnitOfWork): ActorUnitOfWork =
    ActorUnitOfWork(
      key = fromProtoActorKey(uow.key),
      actorVersion = uow.actorVersion,
      events = uow.events.map(fromProtoEvent),
      startingEventRank = uow.startingEventRank
    )

  private def serialize(event: Event): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(event)
    oos.close()
    baos.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): Event = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val obj = ois.readObject()
    ois.close()
    obj.asInstanceOf[Event]
  }
}
