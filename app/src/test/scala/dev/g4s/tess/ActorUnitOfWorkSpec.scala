package dev.g4s.tess

import org.scalatest.funsuite.AnyFunSuite
import dev.g4s.tess.core._

class ActorUnitOfWorkSpec extends AnyFunSuite {

  // Simple test doubles
  case class TestId(value: Long) extends Id
  case object E1 extends Event
  case object E2 extends Event

  class TestActor(val id: TestId) extends Actor {
    override type ActorIdType = TestId
    override def receive: PartialFunction[Message, Seq[Message.Reaction]] = PartialFunction.empty
    override def update(event: Event): Actor = this
  }

  test("endingReactionRank should be starting + size - 1") {
    val uow = ActorUnitOfWork(ActorKey(TestId(1), classOf[TestActor]), 1, Seq(E1, E2), startingReactionRank = 10)
    assert(uow.endingReactionRank == 11)
  }

  test("headMessage should return first reaction and None when there is only one reaction") {
    val uow = ActorUnitOfWork(ActorKey(TestId(1), classOf[TestActor]), 1, Seq(E1), startingReactionRank = 5)
    val (head, next) = uow.headEnvelope
    assert(head.exists(_.message == EventMessage(E1)))
    assert(next.isEmpty)
  }

  test("headMessage should return first reaction and the rest as next UnitOfWork") {
    val uow = ActorUnitOfWork(ActorKey(TestId(1), classOf[TestActor]), 3, Seq(E1, E2), startingReactionRank = 7)
    val (head, next) = uow.headEnvelope
    assert(head.exists(_.message == EventMessage(E1)))
    val nextUow = next.get
    assert(nextUow.actorVersion == 4) // increased by 1
    assert(nextUow.startingReactionRank == uow.endingReactionRank + 1)
    assert(nextUow.events == Seq(E2))
  }
}
