package dev.g4s.tess

import scala.util.Try


case class FirstActorMessage(cid: Long, actorIds: List[Long], text: String) extends Message
case class FirstActorCreatedEvent(cid: Long, actorId: StandardId, text: String) extends Event
case class FirstActorUpdated(cid: Long, actorId: StandardId, text: String) extends Event

object FirstActorFactory extends ActorFactory {
  override type ActorIdType = StandardId
  override type ActorType = FirstActor
  override def actorClass: Class[? <: Actor] = classOf[FirstActor]

  override def route: PartialFunction[Message, List[StandardId]] = {
    case FirstActorMessage(_,ids,_) => ids.map(StandardId(_))
  }
  override def receive(id: ActorIdType): PartialFunction[Message, Event] = {
    case FirstActorMessage(cid,ids,text) => FirstActorCreatedEvent(cid, id, text)
  }
  override def create(id: StandardId): PartialFunction[Event, FirstActor] = {
    case FirstActorCreatedEvent(cid, aid, text) => FirstActor(id,0,"")//is populated by the message
  }
}

case class StandardId(id: Long) extends Id//better to have different Ids but in order to make serialization easier....
case class FirstActor(id: StandardId, cid : Long, text: String) extends Actor {
  override type ActorIdType = StandardId
  override def receive: PartialFunction[Message, Seq[Event]] = {
    case FirstActorMessage(cid,_,txt) => Seq(FirstActorUpdated(cid,id,text + txt))
  }

  override def update(event: Event): Actor = event match {
    case FirstActorUpdated(cid, _, txt) => copy(cid = cid, text = txt)
  }
}

case class SecondActorCreatedEvent(cid: Long, actorId: StandardId, text: String) extends Event
case class SecondActorUpdated(cid: Long, actorId: StandardId, text: String) extends Event

object SecondActorFactory extends ActorFactory {
  override type ActorIdType = StandardId
  override type ActorType = SecondActor
  override def actorClass: Class[? <: Actor] = classOf[SecondActor]

  override def route: PartialFunction[Message, List[StandardId]] = {
    case EventMessage(FirstActorCreatedEvent(_,id,_)) => StandardId(id.id) :: Nil
    case EventMessage(FirstActorUpdated(_,id,_)) => StandardId(id.id) :: Nil
  }
  override def receive(id: ActorIdType): PartialFunction[Message, Event] = {
    case EventMessage(FirstActorCreatedEvent(cid,id,text)) => SecondActorCreatedEvent(cid, StandardId(id.id), text)
  }
  override def create(id: StandardId): PartialFunction[Event, SecondActor] = {
    case SecondActorCreatedEvent(cid, aid, text) => SecondActor(id,0,"")//is populated by the message
  }
}

case class SecondActor(id: StandardId, cid : Long, text: String) extends Actor {
  override type ActorIdType = StandardId
  override def receive: PartialFunction[Message, Seq[Event]] = {
    case EventMessage(FirstActorCreatedEvent(_,id,text)) => Seq(SecondActorUpdated(cid,id,s"First actor inserted with text: ${text}"))
    case EventMessage(FirstActorUpdated(_,id,text)) => Seq(SecondActorUpdated(cid,id,s"First actor updated with text: ${text}"))
  }

  override def update(event: Event): Actor = event match {
    case SecondActorUpdated(cid, _, txt) => copy(cid = cid, text = txt)
  }
}

