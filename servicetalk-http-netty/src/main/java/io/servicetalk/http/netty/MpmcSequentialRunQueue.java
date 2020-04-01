/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.netty;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * This queue allows for Muli-Producer Multi-Consumer (Mpmc) threading semantics while also invoking the head
 * {@link Node}'s {@link Node#run()} in a serial fashion. The {@link Node#run()} will eventually trigger a
 * {@link #poll(Node)} (possibly asynchronously on another thread) which will invoke {@link Node#run()} on the next head
 * (assuming one exists).
 * <p>
 * Although this queue supports Multi-Consumer threading semantics the {@link #poll(Node)} is typically only invoked
 * from a single thread (assuming successful runnable completion), it maybe invoked multiple times (potentially from
 * different threads) with the same {@link Node} due to cancellation/failure.
 */
final class MpmcSequentialRunQueue {
    private static final AtomicReferenceFieldUpdater<MpmcSequentialRunQueue, Node>
            tailUpdater = newUpdater(MpmcSequentialRunQueue.class, Node.class, "tail");

    @Nullable
    private volatile Node tail;

    /**
     * Offer {@link Node} to this queue.
     * @param node The {@link Node} to append. Must only exclusively be offered to this queue instance.
     */
    void offer(final Node node) {
        for (;;) {
            Node tail = tailUpdater.get(this);
            if (tail == null) {
                if (tailUpdater.compareAndSet(this, null, node)) {
                    // node has been inserted and is the only node, we initiate processing.
                    node.run();
                    break;
                }
                // Another thread won the race to offer a node, loop around and try again.
            } else if (tail.append(node)) {
                // Make the newly appended node visible as the tail. This is a best effort CAS and may fail because:
                // 1. Another thread is also inserting, has a stale tail, followed its existing tail links, and updated
                // the tail reference via offerPatchTail.
                // 2. The consumer thread has seen the link from the old tail to the new node, processed node,
                // popped node from the list (updated node's next to point to EMPTY_NODE), another producer thread
                // appends a new node, sees the tail is popped, and updates the tail reference via CAS.
                tailUpdater.compareAndSet(this, tail, node);
                break;
            } else if (tail.isPopped()) {
                // A previously appended node was processed, and popped before updating the tail after append. In that
                // case the tail maybe pointing to an invalid node and we clear it out.
                if (tailUpdater.compareAndSet(this, tail, node)) {
                    node.run();
                    break;
                }
                // Best effort to clear the tail, and failure is OK because:
                // 1. Another thread is in offer and already patched up the tail pointer and we will read the new tail
                // on the next loop iteration.
            } else if (offerPatchTail(node, tail)) {
                break;
            }
        }
    }

    private boolean offerPatchTail(final Node node, final Node tail) {
        Node currentTail = tailUpdater.get(this);
        if (currentTail == tail) {
            // tail is stale so attempt to iterate through the linked list and update tail.
            currentTail = tail.iterateToTail();
            if (currentTail.isPopped()) {
                if (tailUpdater.compareAndSet(this, tail, node)) {
                    node.run();
                    return true;
                }
            } else {
                tailUpdater.compareAndSet(this, tail, currentTail);
            }
            // Best effort to update/clear the tail, and failure is OK because:
            // 1. Another thread is in offer and already patched up the tail pointer and we will read the new
            // tail on the next loop iteration.
        }
        return false;
    }

    /**
     * Pop {@code node} from this queue. This will invoke {@link Node#run()} on the next head of the queue
     * (if one exists).
     * <p>
     * Typically invoked by a single thread but maybe invoked by multiple threads, and maybe invoked multiple times.
     * Re-entry from {@link Node#run()} from another thread is permitted.
     * @param head A {@link Node} which has been passed to {@link #offer(Node)}, and whose {@link Node#run()} has been
     * invoked by this queue.
     */
    void poll(final Node head) {
        // This method maybe called multiple times on the same node, in which case next will be EMPTY_NODE and the run
        // method will be a noop.
        Node next = head.pop();
        if (next != null) {
            next.run();
        } else {
            tailUpdater.compareAndSet(this, head, null);
            // Best effort to clear the tail, and failure is OK because:
            // 1. Another thread appended this head, but has not yet updated the tail. In this case the tail will be
            // stale (e.g. pointing to head node that has already been processed) and corrected by future inserts.
        }
    }

    /**
     * Single linked node in the queue.
     * <p>
     * Instances are single use only! No re-use or sharing between queue instances!
     */
    abstract static class Node {
        /**
         * the EMPTY_NODE's next must point to itself so that attempts to {@link #append(Node)} on it will fail,
         * and also be detected as {@link #isPopped()} and not prevent any new nodes from being
         * {@link #offer(Node) offered}.
         */
        private static final Node EMPTY_NODE = new Node(false) {
            @Override
            void run() {
            }
        };
        private static final AtomicReferenceFieldUpdater<Node, Node> nextUpdater =
                newUpdater(Node.class, Node.class, "next");
        @Nullable
        private volatile Node next;

        Node() {
        }

        private Node(@SuppressWarnings("unused") boolean emptyNodeIgnored) {
            this.next = this;
        }

        abstract void run();

        private boolean append(Node next) {
            return nextUpdater.compareAndSet(this, null, next);
        }

        @Nullable
        private Node pop() {
            return nextUpdater.getAndSet(this, EMPTY_NODE);
        }

        private boolean isPopped() {
            return next == EMPTY_NODE;
        }

        private Node iterateToTail() {
            Node prev = this;
            Node next = prev.next;
            while (next != null) {
                prev = next;
                next = next.next;
            }
            return prev;
        }
    }
}
