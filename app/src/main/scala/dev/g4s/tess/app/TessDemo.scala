package dev.g4s.tess.app

import dev.g4s.tess.Tess
import dev.g4s.tess.coordinator.MemorizingDispatcher
import dev.g4s.tess.core.ActorUnitOfWork
import dev.g4s.tess.domain.{AddItemsForCustomer, BasketFactory, BasketId, ClearBasket, CustomerFactory, ListBasket}
import dev.g4s.tess.store.InMemoryEventStore
import dev.g4s.tess.syntax.all._


object TessDemo {
  def main(args: Array[String]): Unit = {
    val es = Tess
      .builder
      .withActorFactories(CustomerFactory, BasketFactory)
      .withEventStore(() => new InMemoryEventStore())
      .withDispatcher(() => new MemorizingDispatcher())
      .build()

    val events= es.process(AddItemsForCustomer(1,List(2,3), "apples,bananas")).fold(throw _,identity)
    print(events)
    val events2 = es.process(AddItemsForCustomer(2,List(2,3), "oranges")).fold(throw _,identity)
    print(events2)
    val events3 = es.process(AddItemsForCustomer(3,List(2,3), "grapes")).fold(throw _,identity)
    print(events3)

    val directBasket = es.process(ListBasket(4, List(2,3))).fold(throw _, identity)
    print(directBasket) 
    val directBasket1 = es.process(ClearBasket.to(BasketId(3))).fold(throw _, identity)
    print(directBasket1) 
    val directBasket2 = es.process(ListBasket(4, List(2,3))).fold(throw _, identity)
    print(directBasket2)
  }

  private def print(uows: Seq[ActorUnitOfWork]) : Unit=  {
    println(s"\nGenerated ${uows.size} UoWs")
     println(uows.map{uow => s"${uow.startingReactionRank}: ${uow.events.mkString("->")}"}.mkString("\n"))
  }
}
