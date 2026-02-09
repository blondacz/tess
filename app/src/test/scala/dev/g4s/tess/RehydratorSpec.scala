package dev.g4s.tess

import org.scalatest.funsuite.AnyFunSuite

class RehydratorSpec extends AnyFunSuite {

  test("rehydrateNewActor should build actor from first UOW events and keep version of last UOW") {
    val uow1 = ActorUnitOfWork(
      ActorKey(CustomerId(42), classOf[Customer]),
      actorVersion = 1,
      reactions = Seq(CustomerCreated(1, CustomerId(42)), CustomerUpdated(1, CustomerId(42), "apples")),
      startingReactionRank = 1
    )

    val uow2 = ActorUnitOfWork(
      ActorKey(CustomerId(42), classOf[Customer]),
      actorVersion = 2,
      reactions = Seq(CustomerUpdated(2, CustomerId(42), "apples,oranges")),
      startingReactionRank = 3
    )

    val id = CustomerId(42)
    val rehydrated = Rehydrator.rehydrate(id, List(uow1, uow2))(CustomerFactory)
    assert(rehydrated.nonEmpty)
    val (actor, version) = rehydrated.get
    assert(version == 2)
    val customer = actor.asInstanceOf[Customer]
    assert(customer.id == id)
    assert(customer.cid == 2)
  }
}
