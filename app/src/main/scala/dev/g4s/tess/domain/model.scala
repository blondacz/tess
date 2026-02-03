package dev.g4s.tess.domain

import dev.g4s.tess.core._

// Messages / events now model a simple Customer -> Basket flow.
case class AddItemsForCustomer(cid: Long, customerIds: List[Long], itemsCsv: String) extends Message
case class ListBasket(cid: Long, basketIds: List[Long]) extends Message
case class CustomerCreated(cid: Long, customerId: CustomerId, initialItemsCsv: String) extends Event
case class CustomerUpdated(cid: Long, customerId: CustomerId, itemsCsv: String) extends Event

object CustomerFactory extends ActorFactory {
  override type ActorIdType = CustomerId
  override type ActorType = Customer
  override def actorClass: Class[? <: Actor] = classOf[Customer]

  override def route: PartialFunction[Message, List[CustomerId]] = {
    case AddItemsForCustomer(_, ids, _) => ids.map(CustomerId(_))
  }
  override def receive(id: ActorIdType): PartialFunction[Message, Event] = {
    case AddItemsForCustomer(cid, _, items) => CustomerCreated(cid, id, items)
  }
  override def create(id: CustomerId): PartialFunction[Event, Customer] = {
    case CustomerCreated(_, _, _) => Customer(id, cid = 0) // state populated by events
  }
}

case class CustomerId(id: Long) extends Id

case class Customer(id: CustomerId, cid: Long) extends Actor {
  override type ActorIdType = CustomerId

  override def receive: PartialFunction[Message, Seq[Event]] = {
    case AddItemsForCustomer(cid, _, itemsCsv) =>
      Seq(CustomerUpdated(cid, id, itemsCsv))
  }

  override def update(event: Event): Actor = event match {
    case CustomerUpdated(cid, _, _)    => copy(cid = cid)
  }
}


case class BasketId(id: Long) extends Id

case class BasketCreated(cid: Long, basketId: BasketId) extends Event
case class BasketUpdated(cid: Long, basketId: BasketId, itemsCsv: String) extends Event
case class BasketListed(cid: Long, basketId: BasketId, itemsCsv: String) extends Event

object BasketFactory extends ActorFactory {
  override type ActorIdType = BasketId
  override type ActorType = Basket
  override def actorClass: Class[? <: Actor] = classOf[Basket]

  override def route: PartialFunction[Message, List[BasketId]] = {
    case EventMessage(CustomerUpdated(_, id, _))     => BasketId(id.id) :: Nil
    case ListBasket(_, ids)                          => ids.map(BasketId(_))
  }
  override def receive(id: ActorIdType): PartialFunction[Message, Event] = {
    case EventMessage(CustomerUpdated(cid, customerId, _)) => BasketCreated(cid, BasketId(customerId.id))
  }

  override def create(id: BasketId): PartialFunction[Event, Basket] = {
    case BasketCreated(_, _) => Basket(id, cid = 0) // state populated by events
  }
}

case class Basket(id: BasketId, cid: Long, items: List[String] = Nil) extends Actor {
  override type ActorIdType = BasketId

  override def receive: PartialFunction[Message, Seq[Event]] = {
    case EventMessage(CustomerUpdated(cid, _, items)) =>
      Seq(BasketUpdated(cid, id, items))
    case ListBasket(cid, _) =>
      Seq(BasketListed(cid, id, Basket.render(items)))
  }

  override def update(event: Event): Actor = event match {
    case BasketUpdated(cid, _, itemsCsv) => copy(cid = cid, items = items ++ Basket.parse(itemsCsv))
    case BasketListed(_, _, _)           => this
  }
}

object Basket {
  private def parse(csv: String): List[String] =
    csv.split(",").toList.map(_.trim).filter(_.nonEmpty)

  private def render(items: List[String]): String =
    items.mkString(",")
}
