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
package io.servicetalk.concurrent.api;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ThrowableUtils.catchUnexpected;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class ClosableConcurrentStack<T> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<ClosableConcurrentStack, Object> topUpdater =
            newUpdater(ClosableConcurrentStack.class, Object.class, "top");
    @Nullable
    private volatile Object top;

    /**
     * Push an item onto the stack.
     * @param item the item to push onto the stack.
     * @return {@code true} if the operation was successful. {@code false} if {@link #close(Consumer)} has been called
     * and {@code item} has been consumed via {@link Consumer#accept(Object)} of the {@link #close(Consumer)} argument.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    boolean push(T item) {
        final Node<T> newTop = new Node<>(item);
        for (;;) {
            final Object rawOldTop = top;
            if (rawOldTop != null && !Node.class.equals(rawOldTop.getClass())) {
                ((Consumer<T>) rawOldTop).accept(item);
                return false;
            }
            newTop.next = (Node<T>) rawOldTop;
            if (topUpdater.compareAndSet(this, rawOldTop, newTop)) {
                return true;
            }
        }
    }

    /**
     * Best effort removal of {@code item} from this stack.
     * @param item The item to remove.
     * @return {@code true} if the item was found in this stack and marked for removal. The "relaxed" nature of
     * this method means {@code true} might be returned in the following scenarios without external synchronization:
     * <ul>
     *     <li>invoked multiple times with the same {@code item} from different threads</li>
     *     <li>{@link #close(Consumer)} removes this item from another thread</li>
     * </ul>
     */
    boolean relaxedRemove(T item) {
        final Object rawCurrTop = top;
        if (item == null || rawCurrTop == null || !Node.class.equals(rawCurrTop.getClass())) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Node<T> curr = (Node<T>) rawCurrTop;
        Node<T> prev = null;
        do {
            if (item.equals(curr.item)) {
                // Best effort Node removal. close will discard the Node if this attempt isn't visible to all threads.
                curr.item = null;
                if (prev != null) {
                    prev.next = curr.next;
                    // Don't set curr.next to null! If multiple threads are removing items at the same time removed
                    // Nodes maybe "resurrected" and if links are nulled the stack may lose references to Nodes after
                    // the removal point. Just let the GC reclaim the Node when it is ready because it is no longer
                    // referenced from top, or if "resurrected" the "relaxed" contract of this method allows it.
                } else if (!topUpdater.compareAndSet(this, curr, curr.next)) {
                    removeNode(curr);
                }
                return true;
            } else {
                prev = curr;
                curr = curr.next;
            }
        } while (curr != null);
        return false;
    }

    private void removeNode(final Node<T> nodeToRemove) {
        for (;;) {
            final Object rawCurrTop = top;
            if (rawCurrTop == null || !Node.class.equals(rawCurrTop.getClass())) {
                break;
            }
            boolean failedTopRemoval = false;
            @SuppressWarnings("unchecked")
            Node<T> curr = (Node<T>) rawCurrTop;
            Node<T> prev = null;
            do {
                if (curr == nodeToRemove) {
                    if (prev == null) {
                        failedTopRemoval = !topUpdater.compareAndSet(this, curr, curr.next);
                        break;
                    } else {
                        prev.next = curr.next;
                        return;
                    }
                }
                prev = curr;
                curr = curr.next;
            } while (curr != null);

            // If we attempted and failed to remove the top node, try again. Otherwise, if we removed the top node or if
            // we didn't find the node in the stack then we are done and can break (another thread may have removed the
            // same node concurrently).
            if (!failedTopRemoval) {
                break;
            }
        }
    }

    /**
     * Clear the stack contents, and prevent future {@link #push(Object)} operations from adding to this stack.
     * {@code closer} will be invoked for each element currently in the stack.
     * @param closer Invoked for each element currently in the stack. Elements that were previously
     * {@link #relaxedRemove(Object) relaxRemoved} may still invoked by {@code closer}.
     */
    void close(Consumer<T> closer) {
        requireNonNull(closer);
        Object rawOldTop;
        for (;;) {
            rawOldTop = top;
            if (rawOldTop == null || Node.class.equals(rawOldTop.getClass())) {
                if (topUpdater.compareAndSet(this, rawOldTop, closer)) {
                    break;
                }
            } else {
                return;
            }
        }
        @SuppressWarnings("unchecked")
        Node<T> oldTop = (Node<T>) rawOldTop;
        Throwable delayedCause = null;
        while (oldTop != null) {
            final T item = oldTop.item;
            oldTop = oldTop.next;
            if (item != null) {
                try {
                    closer.accept(item);
                } catch (Throwable cause) {
                    delayedCause = catchUnexpected(delayedCause, cause);
                }
            }
        }

        if (delayedCause != null) {
            throwException(delayedCause);
        }
    }

    private static final class Node<T> {
        @Nullable
        T item;
        @Nullable
        Node<T> next;

        Node(T item) {
            this.item = requireNonNull(item);
        }
    }
}
