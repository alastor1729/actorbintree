/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import actorbintree.BinaryTreeNode.{CopyFinished, CopyTo}
import actorbintree.BinaryTreeSet.{Contains, ContainsResult, Insert, OperationFinished, Remove}
import akka.actor._
import akka.event.LoggingReceive

import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}

//Binary tree data structure
//sealed trait Tree[A]
//case object Leaf extends Tree[Nothing]
//case class Node[A](a: A, left: Tree[A], right: Tree[A]) extends Tree[A]


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  // TODO - Part 1:
  val normal: Receive = LoggingReceive {
    case op: Operation => root.forward(op)

    case GC =>
      val newRoot = createRoot
      context.become(garbageCollecting(newRoot), false)
      root ! CopyTo(newRoot)
  }

  // optional
  /** Handles messages while garbage collection is performed.
    *
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  // TODO - Part 2:
  def garbageCollecting(newRoot: ActorRef): Receive = LoggingReceive {
    case CopyFinished =>
      println("Copy done !!!!")
      root = newRoot
      context.unbecome()
      pendingQueue.foreach { root.forward(_) }
      pendingQueue = Queue.empty

    case op: Operation =>
      pendingQueue = pendingQueue.enqueue(op)

    case GC => /* ignore GC while garbage collection */
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  // TODO - Part 3:
  val normal: Receive = LoggingReceive {

    case cReq @ Contains(requester, id, e) =>
      if (e == elem) {
        requester ! ContainsResult(id, !removed)
      }
      else {
//        subtrees.get(nextPos(e)).fold {
//          requester ! ContainsResult(id, result = false)
//        } {
//          branch => branch ! req
//        }
        val next = nextPos(e)
        if(subtrees.isDefinedAt(next)) subtrees(next) ! cReq
        else requester ! ContainsResult(id, result = false)
      }

    case iReq @ Insert(requester, id, e) =>
      if (e == elem) {
        removed = e == 0 // un-remove it, but not at 0 (root).  0 is always removed
        requester ! OperationFinished(id)
      }
      else { //don't care this node is removed or not
        val next = nextPos(e)
        if (subtrees.isDefinedAt(next))
          subtrees(next) ! iReq
        else {
          subtrees += (next -> context.actorOf(props(e, initiallyRemoved = false)))
          requester ! OperationFinished(id)
        }
      }

    case rReq @ Remove(requester, id, e) =>
      if (e == elem) {
        removed = true
        requester ! OperationFinished(id)
      } else { //don't care this node is removed or not
        val next = nextPos(e)
        if (subtrees.isDefinedAt(next)) subtrees(next) ! rReq
        else requester ! OperationFinished(id)
      }

    case copyMsg @ CopyTo(newRoot) =>
      println(s"Start copying in $elem ......")
      val children = subtrees.values.toSet
      context.become(copying(children, false))

      //root 0 is always removed, but still need to reply OperationFinished(-1)
      if (elem == 0) {
        self ! OperationFinished(-1)
      }
      else if (!removed) {
        newRoot ! Insert(self, -1 /* self insert */ , elem)
      }
      children foreach { _ ! copyMsg }

  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  // TODO - Part 4:
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = LoggingReceive {
    case OperationFinished(-1) =>  //copy self is done
      println(s"Copy $elem is done...")
      if (expected.isEmpty) {  //both copy self and copy children is done
        context.parent ! CopyFinished
        self ! PoisonPill
      } else { //still waiting on copying children, but copy self is done
        context.become(copying(expected, true))
      }

    case CopyFinished =>
      println(s"Copy child of $elem is done...")
      val newExpected = expected.filterNot(_ == sender)
      if (newExpected.isEmpty && insertConfirmed) { //both copy self and copy children is done
        context.parent ! CopyFinished
        self ! PoisonPill
      } else { // waiting on children copy to be done
        context.become(copying(newExpected, insertConfirmed))
      }
  }

  private def nextPos(e: Int): Position = {
    if (e > elem) Right
    else Left
  }

}
