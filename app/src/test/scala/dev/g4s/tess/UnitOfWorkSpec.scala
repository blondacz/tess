package dev.g4s.tess

import org.scalatest.funsuite.AnyFunSuite

class UnitOfWorkSpec extends AnyFunSuite {

  // Simple test doubles
  case class TestId(value: Long) extends Id
  case object E1 extends Event
  case object E2 extends Event

  class TestActor(val id: TestId) extends Actor {
    override type ActorIdType = TestId
    override def receive: PartialFunction[Message, Seq[Event]] = PartialFunction.empty
    override def update(event: Event): Actor = this
  }

  test("endingEventRank should be starting + size - 1") {
    val uow = UnitOfWork(ActorKey(TestId(1), classOf[TestActor]), 1, Seq(E1, E2), startingEventRank = 10)
    assert(uow.endingEventRank == 11)
  }

  test("headEvent should return first event and None when there is only one event") {
    val uow = UnitOfWork(ActorKey(TestId(1), classOf[TestActor]), 1, Seq(E1), startingEventRank = 5)
    val (head, next) = uow.headEvent
    assert(head.contains(E1))
    assert(next.isEmpty)
  }

  test("headEvent should return first event and the rest as next UnitOfWork") {
    val uow = UnitOfWork(ActorKey(TestId(1), classOf[TestActor]), 3, Seq(E1, E2), startingEventRank = 7)
    val (head, next) = uow.headEvent
    assert(head.contains(E1))
    val nextUow = next.get
    assert(nextUow.actorVersion == 4) // increased by 1
    assert(nextUow.startingEventRank == uow.endingEventRank + 1)
    assert(nextUow.events == Seq(E2))
  }
}
