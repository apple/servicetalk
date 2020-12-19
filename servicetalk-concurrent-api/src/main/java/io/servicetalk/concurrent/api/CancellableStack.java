/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;

final class CancellableStack implements Cancellable {
    private final ConcurrentStack<Cancellable> stack = new ConcurrentStack<>();

    /**
     * {@inheritDoc}
     * <p>
     * Cancel all {@link Cancellable} that have been previously added via {@link #add(Cancellable)} which have not yet
     * been cancelled, and all future {@link Cancellable}s added via {@link #add(Cancellable)} will also be cancelled.
     */
    @Override
    public void cancel() {
        stack.close(Cancellable::cancel);
    }

    /**
     * Add a {@link Cancellable} that will be cancelled when this object's {@link #cancel()} method is called,
     * or be cancelled immediately if this object's {@link #cancel()} method has already been called.
     * @param toAdd The {@link Cancellable} to add.
     * @return {@code true} if the {@code toAdd} was added. If {@code false} {@link Cancellable#cancel()} is called.
     */
    boolean add(Cancellable toAdd) {
        if (!stack.push(toAdd)) {
            toAdd.cancel();
            return false;
        }
        return true;
    }

    /**
     * Best effort removal of {@code item} from this stack.
     * @param toRemove The item to remove.
     * {@code true} if the item was found in this stack and marked for removal. The "relaxed" nature of
     * this method means {@code true} might be returned in the following scenarios without external synchronization:
     * <ul>
     *   <li>invoked multiple times with the same {@code item} from different threads</li>
     *   <li>{@link #cancel()} removes this item from another thread</li>
     * </ul>
     * @see ConcurrentStack#relaxedRemove(Object)
     */
    boolean relaxedRemove(Cancellable toRemove) {
        return stack.relaxedRemove(toRemove);
    }
}
