package dev.g4s.tess

import com.google.protobuf.ByteString
import dev.g4s.tess.core.{Actor, ActorKey, ActorUnitOfWork, Event, Id}
import dev.g4s.tess.domain.{Basket, BasketId, Customer, CustomerId}
import dev.g4s.tess.raft.v1.tess.{ActorKey => ProtoActorKey, ActorUnitOfWork => ProtoActorUnitOfWork, EventEnvelope => ProtoEventEnvelope}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

object proto {

  def toProtoActorKey(key: ActorKey): ProtoActorKey =
    ProtoActorKey(
      actorType = key.clazz.getName,
      actorId = idAsLong(key.id)
    )

  def fromProtoActorKey(pk: ProtoActorKey): ActorKey = {
    val clazz: Class[? <: Actor] = Class.forName(pk.actorType).asInstanceOf[Class[? <: Actor]]
    val id: Id = idFromClass(clazz, pk.actorId)
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

  private def idAsLong(id: Id): Long = id match {
    case CustomerId(v) => v
    case BasketId(v)   => v
    case other =>
      other.productIterator.toList.headOption match {
        case Some(v: Long) => v
        case _             => throw new IllegalArgumentException(s"Unsupported id type: ${other.getClass}")
      }
  }

  private def idFromClass(clazz: Class[?], value: Long): Id = {
    if (classOf[Customer].isAssignableFrom(clazz)) CustomerId(value)
    else if (classOf[Basket].isAssignableFrom(clazz)) BasketId(value)
    else new Id {
      override def productArity: Int = 1
      override def productElement(n: Int): Any = if n == 0 then value else throw new IndexOutOfBoundsException(n)
      override def canEqual(that: Any): Boolean = that.isInstanceOf[Id]
      override def productPrefix: String = "GenericLongId"
    }
  }
}
