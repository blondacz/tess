package dev.g4s.tess

import org.scalatest.funsuite.AnyFunSuite

class RehydratorSpec extends AnyFunSuite {

  test("rehydrateNewActor should build actor from first UOW events and keep version of last UOW") {
    val id = StandardId(42)

    val uow1 = ActorUnitOfWork(
      ActorKey(id, classOf[FirstActor]),
      actorVersion = 1,
      events = Seq(FirstActorCreatedEvent(1, id, "hi"), FirstActorUpdated(1, id, "hi")),
      startingEventRank = 1
    )

    val uow2 = ActorUnitOfWork(
      ActorKey(id, classOf[FirstActor]),
      actorVersion = 2,
      events = Seq(FirstActorUpdated(2, id, "there")),
      startingEventRank = 3
    )

    val rehydrated = Rehydrator.rehydrate(id, List(uow1, uow2))(FirstActorFactory)
    assert(rehydrated.nonEmpty)
    val (actor, version) = rehydrated.get
    assert(version == 2)
    val fa = actor.asInstanceOf[FirstActor]
    assert(fa.id == id)
    assert(fa.cid == 2)
    assert(fa.text == "there")
  }
}
