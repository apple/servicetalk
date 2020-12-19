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

final class RelaxedClosableConcurrentStack<T, C> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<RelaxedClosableConcurrentStack, Object> topUpdater =
            newUpdater(RelaxedClosableConcurrentStack.class, Object.class, "top");
    @Nullable
    private volatile Object top;

    /**
     * Push an item onto the stack.
     * @param item the item to push onto the stack.
     * @return {@code null} if the operation was successful. Otherwise the state from {@link #close(Consumer, Object)}
     * if this stack has been closed.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    C push(T item) {
        final Node<T> newTop = new Node<>(item);
        for (;;) {
            final Object rawOldTop = top;
            if (rawOldTop != null && !Node.class.equals(rawOldTop.getClass())) {
                return (C) rawOldTop;
            }
            newTop.next = (Node<T>) rawOldTop;
            if (topUpdater.compareAndSet(this, rawOldTop, newTop)) {
                return null;
            }
        }
    }

    /**
     * Pop the top element from the stack.
     * <p>
     * Elements that were previously {@link #relaxedRemove(Object) relaxRemoved} may still be returned by this method.
     * @return {@code null} if the stack is empty, or the top element of the stack.
     */
    @Nullable
    T relaxedPop() {
        for (;;) {
            final Object rawOldTop = top;
            if (rawOldTop == null || !Node.class.equals(rawOldTop.getClass())) {
                return null;
            } else {
                @SuppressWarnings("unchecked")
                final Node<T> oldTop = (Node<T>) rawOldTop;
                if (topUpdater.compareAndSet(this, oldTop, oldTop.next)) {
                    final T item = oldTop.item;
                    if (item != null) { // best effort to avoid previously removed items.
                        return item;
                    }
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
     *     <li>{@link #relaxedPop()} removes this item from another thread</li>
     * </ul>
     */
    boolean relaxedRemove(T item) {
        final Object rawCurrTop = top;
        if (item == null || rawCurrTop == null || !Node.class.equals(rawCurrTop.getClass())) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Node<T> currTop = (Node<T>) rawCurrTop;
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

    /**
     * Clear the stack contents, and prevent future {@link #push(Object)} operations from adding to this stack.
     * {@code closer} will be invoked for each element currently in the stack.
     * @param closer Invoked for each element currently in the stack. Elements that were previously
     * {@link #relaxedRemove(Object) relaxRemoved} may still invoked by {@code closer}.
     * @param state The state to return for future {@link #push(Object)} calls, which indicates this stack has been
     * closed.
     */
    void close(Consumer<T> closer, C state) {
        requireNonNull(closer);
        requireNonNull(state); // push returns null for success or this state if closed, so it can't be null.
        Object rawOldTop;
        for (;;) {
            rawOldTop = top;
            if (rawOldTop == null || Node.class.equals(rawOldTop.getClass())) {
                if (topUpdater.compareAndSet(this, rawOldTop, state)) {
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
