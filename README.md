# Akka Actors Binary Tree

## Resource:
https://www.edx.org/course/programming-reactive-systems

## Objective:
* Using Scala and Akka Actors, create an actor-based binary tree set where each node is represented by one actor

## from the edX Assignment Page:
Binary Trees:

Binary trees are tree based data structures where every node has at most two children (left and right). In this exercise, every node stores an integer element.
From this we can build a binary search tree by requiring for every node that:

* values of elements in the left subtree are strictly smaller than the node's element
* values of elements in the right subtree are strictly bigger than the node's element

In addition, there should be no duplicates, hence we obtain a binary tree set.

Your task in this assignment is to implement an actor-based binary tree set where each node is represented by one actor. The advantage of such an actor-based solution is that it can execute fully asynchronously and in parallel.


The API:

You can find the message-based API for the actor-based binary tree to be implemented in the supplied BinaryTreeSet object in the file BinaryTreeSet.scala.

The operations, represented by actor messages, that the implementation should support are the following:
* Insert
* Remove
* Contains

All three of the operations expect an ActorRef representing the requester of the operation, a numerical identifier of the operation and the element itself. Insert and Remove operations should result in an OperationFinished message sent to the provided requester ActorRef reference including the id of the operation. Insert and Remove should return an OperationFinished message even if the element was already present in the tree or was not found, respectively. Contains should result in a ContainsResult message containing the result of the lookup (a Boolean which is true if and only if the element is in the tree when the query arrives) and the identifier of the Contains query.


Handling of Removal:

You should observe that both the Insert and Contains operations share an important property, namely, they only traverse a linear path from the root of the tree to the appropriate inner node or leaf. Since the tree nodes are actors which process messages one-by-one, no additional synchronization is needed between these operations. Removal in a binary tree unfortunately results in tree restructuring, which means that nodes would need to communicate and coordinate between each other (while additional operations arrive from the external world!).

Therefore, instead of implementing the usual binary tree removal, in your solution you should use a flag that is stored in every tree node (removed) indicating whether the element in the node has been removed or not. This will result in a very simple implementation that is concurrent and correct with minimal effort. Unfortunately this decision results in the side effect that the tree set accumulates "garbage" (elements that have been removed) over time.

Garbage Collection:

As we have seen, removal of entries can be implemented simply by using a removal flag with the added cost of growing garbage over time. To overcome this limitation you will need to implement a "garbage collection" feature. Whenever your binary tree set receives a GC message, it should clean up all the removed elements, while additional operations might arrive from the external world.

The garbage collection task can be implemented in two steps. The first subtask is to implement an internal CopyTo operation on the binary tree that copies all its non-removed contents from the binary tree to a provided new one. This implementation can assume that no operations arrive while the copying happens (i.e. the tree is protected from modifications while copying takes places).

The second part of the implementation is to implement garbage collection in the manager (BinaryTreeSet) by using the copy operation. The newly constructed tree should replace the old one and all actors from the old one should be stopped. Since copying assumes no other concurrent operations, the manager should handle the case when operations arrive while still performing the copy in the background. It is your responsibility to implement the manager in such a way that the fact that garbage collection happens is invisible from the outside (of course additional delay is allowed). For the sake of simplicity, your implementation should ignore GC requests that arrive while garbage collection is taking place.


Assignment task:

You can find code stubs in the file BinaryTreeSet.scala which provides you with the API as described above, the BinaryTreeSet and BinaryTreeNode classes. The BinaryTreeSet represents the whole binary tree. This is also the only actor that is explicitly created by the user and the only actor the user sends messages to.

You can implement as many or as few message handlers as you like and you can add additional variables or helper functions. We provide suggestions in your code stub, marked with the comment optional, but you are free to use it fully or partially; the optional elements are not part of the tested API.

To see a binary tree in operation check our provided tests in BinaryTreeSuite.scala. Note in particular that it is the user who triggers garbage collection by sending a GC message (for the sake of simplicity of this exercise).

Don't forget to make sure that no Operation messages interfere during garbage collection and that the user does not receive any messages that may result from the copying process. To achieve this, put any incoming operation message into a queue during the garbage collection process. When the copying process is finished, re-send the queued messages to the tree root and clear the queue.
