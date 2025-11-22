package dev.g4s.heatwise.es

import scala.util.Try


case class FirstActorMessage(cid: Long, actorIds: List[Long], text: String) extends Message
case class FirstActorCreatedEvent(cid: Long, actorId: FirstActorId, text: String) extends Event
case class FirstActorUpdated(cid: Long, actorId: FirstActorId, text: String) extends Event

object FirstActorFactory extends ActorFactory {
  override type ActorIdType = FirstActorId
  override type ActorType = FirstActor

  override def route: PartialFunction[Message, List[FirstActorId]] = {
    case FirstActorMessage(_,ids,_) => ids.map(FirstActorId(_))
  }
  override def receive(id: ActorIdType): PartialFunction[Message, Event] = {
    case FirstActorMessage(cid,ids,text) => FirstActorCreatedEvent(cid, id, text)
  }
  override def create(id: FirstActorId): PartialFunction[Event, FirstActor] = {
    case FirstActorCreatedEvent(cid, aid, text) => FirstActor(id,0,"")//is populated by the message
  }
}

case class FirstActorId(id: Long) extends Id
case class FirstActor(id: FirstActorId, cid : Long, text: String) extends Actor {
  override type ActorIdType = FirstActorId
  override def receive: PartialFunction[Message, Seq[Event]] = {
    case FirstActorMessage(cid,_,txt) => Seq(FirstActorUpdated(cid,id,text + txt))
  }

  override def update(event: Event): Actor = event match {
    case FirstActorUpdated(cid, _, txt) => copy(cid = cid, text = txt)
  }
}

case class SecondActorCreatedEvent(cid: Long, actorId: SecondActorId, text: String) extends Event
case class SecondActorUpdated(cid: Long, actorId: FirstActorId, text: String) extends Event

object SecondActorFactory extends ActorFactory {
  override type ActorIdType = SecondActorId
  override type ActorType = SecondActor

  override def route: PartialFunction[Message, List[SecondActorId]] = {
    case EventMessage(FirstActorCreatedEvent(_,id,_)) => SecondActorId(id.id) :: Nil
    case EventMessage(FirstActorUpdated(_,id,_)) => SecondActorId(id.id) :: Nil
  }
  override def receive(id: ActorIdType): PartialFunction[Message, Event] = {
    case EventMessage(FirstActorCreatedEvent(cid,id,text)) => SecondActorCreatedEvent(cid, SecondActorId(id.id), text)
  }
  override def create(id: SecondActorId): PartialFunction[Event, SecondActor] = {
    case SecondActorCreatedEvent(cid, aid, text) => SecondActor(id,0,"")//is populated by the message
  }
}

case class SecondActorId(id: Long) extends Id
case class SecondActor(id: SecondActorId, cid : Long, text: String) extends Actor {
  override type ActorIdType = SecondActorId
  override def receive: PartialFunction[Message, Seq[Event]] = {
    case EventMessage(FirstActorCreatedEvent(_,id,text)) => Seq(SecondActorUpdated(cid,id,s"First actor inserted with text: ${text}"))
    case EventMessage(FirstActorUpdated(_,id,text)) => Seq(SecondActorUpdated(cid,id,s"First actor updated with text: ${text}"))
  }

  override def update(event: Event): Actor = event match {
    case SecondActorUpdated(cid, _, txt) => copy(cid = cid, text = txt)
  }
}

