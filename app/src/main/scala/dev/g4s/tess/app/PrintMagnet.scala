package dev.g4s.tess.app

import dev.g4s.tess.core.{ActorUnitOfWork, Notification}

import scala.Conversion

/** Type-class style magnets that know how to pretty-print dispatcher results. */
trait PrintMagnet { def apply(): Unit }

object PrintMagnet {
  given Conversion[List[ActorUnitOfWork], PrintMagnet] with
    def apply(uows: List[ActorUnitOfWork]) = () =>
      println(uows.map(u => s"${u.startingReactionRank}: ${u.reactions.mkString("->")}").mkString("\n"))

  given Conversion[Seq[Notification], PrintMagnet] with
    def apply(ns: Seq[Notification]) = () => println(ns.mkString(","))
}
