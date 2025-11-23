package dev.g4s.tess

import scala.util.Try

object EventSourcingMain {
  def main(args: Array[String]): Unit = {
    val es = new EventSourcedSystem(Seq(FirstActorFactory, SecondActorFactory))
    val events= es.process(FirstActorMessage(1,List(2,3), "hello")).fold(throw _,identity)
    print(events)
    val events2 = es.process(FirstActorMessage(2,List(2,3), ", world")).fold(throw _,identity)
    print(events2)
    val events3 = es.process(FirstActorMessage(3,List(3,3), "!")).fold(throw _,identity)
    print(events3)
  }

  private def print(uows: Seq[ActorUnitOfWork]) : Unit=  {
    println(s"\nGenerated ${uows.size} UoWs")
     println(uows.map{uow => s"${uow.startingEventRank}: ${uow.events.mkString("->")}"}.mkString("\n"))
  }
}

