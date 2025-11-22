package dev.g4s.tess

import org.scalatest.funsuite.AnyFunSuite

class EventSourcingMainSpec extends AnyFunSuite {
  test("main should run without throwing exceptions") {
    // Just a smoke test to ensure the demo main method executes without error
    EventSourcingMain.main(Array.empty)
    succeed
  }
}
