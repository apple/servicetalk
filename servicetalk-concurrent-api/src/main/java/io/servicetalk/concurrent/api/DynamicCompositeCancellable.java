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

/**
 * Provides a means to cascade a {@link #cancel()} call to other {@link Cancellable} objects.
 */
interface DynamicCompositeCancellable extends Cancellable {
    /**
     * {@inheritDoc}
     * <p>
     * Cancel all {@link Cancellable} that have been previously added via {@link #add(Cancellable)} which have not yet
     * been cancelled, and all future {@link Cancellable}s added via {@link #add(Cancellable)} will also be cancelled.
     */
    @Override
    void cancel();

    /**
     * Add a {@link Cancellable} that will be cancelled when this object's {@link #cancel()} method is called,
     * or be cancelled immediately if this object's {@link #cancel()} method has already been called.
     * @param toAdd The {@link Cancellable} to add.
     * @return {@code true} if the {@code toAdd} was added, and {@code false} if {@code toAdd} was not added because
     * it already exists. If {@code false} then {@link Cancellable#cancel()} will be called unless the reason is this
     * implementation does not supporting duplicates and {@code toAdd} has already been added.
     */
    boolean add(Cancellable toAdd);

    /**
     * Remove a {@link Cancellable} such that it will no longer be cancelled when this object's {@link #cancel()} method
     * is called.
     * <p>
     * If this collection doesn't filter out duplicates in {@link #add(Cancellable)}, and duplicates maybe added,
     * this method should be called until it returns {@code false} to remove each duplicate instance.
     * @param toRemove The {@link Cancellable} to remove.
     * @return {@code true} if {@code toRemove} was found and removed.
     */
    boolean remove(Cancellable toRemove);

    /**
     * Determine if {@link #cancel()} has been called.
     *
     * @return {@code true} if {@link #cancel()} has been called.
     */
    boolean isCancelled();
}
