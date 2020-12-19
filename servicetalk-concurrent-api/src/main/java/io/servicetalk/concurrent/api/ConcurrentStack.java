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
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class ConcurrentStack<T> {
    private static final Node<?> CLOSED = new Node<>();
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<ConcurrentStack, Node> topUpdater =
            newUpdater(ConcurrentStack.class, Node.class, "top");
    @Nullable
    private volatile Node<T> top;

    boolean push(T item) {
        final Node<T> newTop = new Node<>(item);
        Node<T> oldTop;
        do {
            oldTop = top;
            if (oldTop == CLOSED) {
                return false;
            }
            newTop.next = oldTop;
        } while (!topUpdater.compareAndSet(this, oldTop, newTop));
        return true;
    }

    @Nullable
    T pop() {
        for (;;) {
            final Node<T> oldTop = top;
            if (oldTop == null || oldTop == CLOSED) {
                return null;
            } else if (topUpdater.compareAndSet(this, oldTop, oldTop.next)) {
                final T item = oldTop.item;
                if (item != null) {
                    return item;
                }
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
     *     <li>{@link #pop()} removes this item from another thread</li>
     * </ul>
     */
    boolean relaxedRemove(T item) {
        requireNonNull(item);
        Node<T> currTop = top;
        while (currTop != null) {
            if (item.equals(currTop.item)) {
                currTop.item = null; // best effort null out the item. pop/close will discard the Node later.
                return true;
            } else {
                currTop = currTop.next;
            }
        }
        return false;
    }

    void close(Consumer<T> closer) {
        @SuppressWarnings("unchecked")
        Node<T> oldTop = topUpdater.getAndSet(this, closedNode());
        Throwable delayedCause = null;
        while (oldTop != null && oldTop != CLOSED) {
            final Node<T> next = oldTop.next;
            final T item = oldTop.item;
            oldTop = next;
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

    @SuppressWarnings("unchecked")
    private static <X> Node<X> closedNode() {
        return (Node<X>) CLOSED;
    }

    private static final class Node<T> {
        @Nullable
        T item;
        @Nullable
        Node<T> next;

        Node() {
            this.item = null;
        }

        Node(T item) {
            this.item = requireNonNull(item);
        }
    }
}
