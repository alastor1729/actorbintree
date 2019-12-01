/**
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.duration._
import scala.util.Random

class BinaryTreeSuite(_system: ActorSystem) extends TestKit(_system) with FunSuiteLike with Matchers with BeforeAndAfterAll with ImplicitSender
{

  def this() = this(ActorSystem("BinaryTreeSuite"))

  override def afterAll: Unit = {
    concurrent.Await.result(system.terminate(), Duration.Inf)
    ()
  }

  import actorbintree.BinaryTreeSet._

  def receiveN(requester: TestProbe, ops: Seq[Operation], expectedReplies: Seq[OperationReply]): Unit =
    requester.within(5.seconds) {
      val repliesUnsorted = for (i <- 1 to ops.size) yield try {
        requester.expectMsgType[OperationReply]
      } catch {
        case ex: Throwable if ops.size > 10 => fail(s"failure to receive confirmation $i/${ops.size}", ex)
        case ex: Throwable                  => fail(s"failure to receive confirmation $i/${ops.size}\nRequests:" + ops.mkString("\n    ", "\n     ", ""), ex)
      }
      val replies = repliesUnsorted.sortBy(_.id)
      if (replies != expectedReplies) {
        val pairs = (replies zip expectedReplies).zipWithIndex filter (x => x._1._1 != x._1._2)
        fail("unexpected replies:" + pairs.map(x => s"at index ${x._2}: got ${x._1._1}, expected ${x._1._2}").mkString("\n    ", "\n    ", ""))
      }
    }

  def verify(probe: TestProbe, ops: Seq[Operation], expected: Seq[OperationReply]): Unit = {
    val topNode = system.actorOf(Props[BinaryTreeSet])

    ops foreach { op =>
      topNode ! op
    }

    receiveN(probe, ops, expected)
    // the grader also verifies that enough actors are created
  }

  test("proper inserts and lookups") {
    val topNode = system.actorOf(Props[BinaryTreeSet])

    topNode ! Contains(testActor, id = 1, 1)
    expectMsg(ContainsResult(1, false))

    topNode ! Insert(testActor, id = 2, 1)
    topNode ! Contains(testActor, id = 3, 1)

    expectMsg(OperationFinished(2))
    expectMsg(ContainsResult(3, true))
    ()
  }

  test("instruction example") {
    val requester = TestProbe()
    val requesterRef = requester.ref
    val ops = List(
      Insert(requesterRef, id=100, 1),
      Contains(requesterRef, id=50, 2),
      Remove(requesterRef, id=10, 1),
      Insert(requesterRef, id=20, 2),
      Contains(requesterRef, id=80, 1),
      Contains(requesterRef, id=70, 2)
      )

    val expectedReplies = List(
      OperationFinished(id=10),
      OperationFinished(id=20),
      ContainsResult(id=50, false),
      ContainsResult(id=70, true),
      ContainsResult(id=80, false),
      OperationFinished(id=100)
      )

    verify(requester, ops, expectedReplies)
  }

  test("behave identically to built-in set (includes GC)") {
    val rnd = new Random()
    def randomOperations(requester: ActorRef, count: Int): Seq[Operation] = {
      def randomElement: Int = rnd.nextInt(100)
      def randomOperation(requester: ActorRef, id: Int): Operation = rnd.nextInt(4) match {
        case 0 => Insert(requester, id, randomElement)
        case 1 => Insert(requester, id, randomElement)
        case 2 => Contains(requester, id, randomElement)
        case 3 => Remove(requester, id, randomElement)
      }

      for (seq <- 0 until count) yield randomOperation(requester, seq)
    }

    def referenceReplies(operations: Seq[Operation]): Seq[OperationReply] = {
      var referenceSet = Set.empty[Int]
      def replyFor(op: Operation): OperationReply = op match {
        case Insert(_, seq, elem) =>
          referenceSet = referenceSet + elem
          OperationFinished(seq)
        case Remove(_, seq, elem) =>
          referenceSet = referenceSet - elem
          OperationFinished(seq)
        case Contains(_, seq, elem) =>
          ContainsResult(seq, referenceSet(elem))
      }

      for (op <- operations) yield replyFor(op)
    }

    val requester = TestProbe()
    val topNode = system.actorOf(Props[BinaryTreeSet])
    val count = 1000

    val ops = randomOperations(requester.ref, count)
    val expectedReplies = referenceReplies(ops)

    ops foreach { op =>
      topNode ! op
      if (rnd.nextDouble() < 0.1) topNode ! GC
    }
    receiveN(requester, ops, expectedReplies)
  }

//  test("instruction example") {
//    val requester = TestProbe()
//    val requesterRef = requester.ref
//    val ops = List(
//      Contains(requesterRef, id=0, 70),
//      Insert(requesterRef, id=1, 2),
//      Insert(requesterRef, id=2, 73),
//      Insert(requesterRef, id=3, 80),
//      Insert(requesterRef, id=4, 77),
//      Contains(requesterRef, id=5, 17),
//      Insert(requesterRef, id=6, 69),
//      Contains(requesterRef, id=7, 92),
//      Insert(requesterRef, id=8, 19),
//      Insert(requesterRef, id=9, 50),
//      Insert(requesterRef, id=10, 3),
//      Insert(requesterRef, id=11, 12),
//      Contains(requesterRef, id=12, 16),
//      Contains(requesterRef, id=13, 44),
//      Contains(requesterRef, id=14, 32),
//      Insert(requesterRef, id=15, 36),
//      Remove(requesterRef, id=16, 80),
//      Contains(requesterRef, id=17, 25)
//    )
//
//    val expectedReplies = List(
//      ContainsResult(0,false),
//      OperationFinished(1),
//      OperationFinished(2),
//      OperationFinished(3),
//      OperationFinished(4),
//      ContainsResult(5,false),
//      OperationFinished(6),
//      ContainsResult(7,false),
//      OperationFinished(8),
//      OperationFinished(9),
//      OperationFinished(10),
//      OperationFinished(11),
//      ContainsResult(12,false),
//      ContainsResult(13,false),
//      ContainsResult(14,false),
//      OperationFinished(15),
//      OperationFinished(16),
//      ContainsResult(17,false)
//    )
//
//    verify(requester, ops, expectedReplies)
//  }

}
