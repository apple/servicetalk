/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.GracefulCloseable;

import java.io.Closeable;
import java.io.IOException;

/**
 * A {@link AsyncCloseable} and {@link Closeable} that allows for adding new {@link AsyncCloseable}s till it is
 * closed.
 */
public interface CompositeCloseable extends AsyncCloseable, GracefulCloseable {

    /**
     * Merges the passed {@link AsyncCloseable} with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of the previously registered {@link AsyncCloseable}s are closed too. This
     * method subscribes to the passed in {@link AsyncCloseable} and the current composite set at the same time.
     * If an order of closure is required, then use {@link #append(AsyncCloseable)} or {@link #prepend(AsyncCloseable)}.
     *
     * @param <T> the type of {@link AsyncCloseable} to be merged
     * @param closeable {@link AsyncCloseable} that is closed when this {@link CompositeCloseable} is closed.
     * @return {@code T}
     */
    <T extends AsyncCloseable> T merge(T closeable);

    /**
     * Merges all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too. This method subscribes to all
     * passed in {@link AsyncCloseable}s and the current composite set at the same time. If an order of closure is
     * required, then use {@link #appendAll(AsyncCloseable...)} or {@link #prependAll(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable mergeAll(AsyncCloseable... asyncCloseables);

    /**
     * Merges all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too. This method subscribes to all
     * passed in {@link AsyncCloseable}s and the current composite set at the same time. If an order of closure is
     * required, then use {@link #appendAll(AsyncCloseable...)} or {@link #prependAll(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable mergeAll(Iterable<? extends AsyncCloseable> asyncCloseables);

    /**
     * Appends the passed {@link AsyncCloseable} with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of the previously registered {@link AsyncCloseable}s are closed too. This
     * method subscribes to the passed in {@link AsyncCloseable} and the current composite set in order.
     * If an order of closure is not required, then use {@link #merge(AsyncCloseable)}.
     *
     * @param <T> the type of {@link AsyncCloseable} to be appended
     * @param closeable {@link AsyncCloseable} that is closed when this {@link CompositeCloseable} is closed.
     * @return {@code T}.
     */
    <T extends AsyncCloseable> T append(T closeable);

    /**
     * Appends all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too. This method subscribes to all
     * passed in {@link AsyncCloseable}s and the current composite set in the order they are passed to this method. If
     * an order of closure is not required, then use {@link #mergeAll(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable appendAll(AsyncCloseable... asyncCloseables);


    /**
     * Appends all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too. This method subscribes to all
     * passed in {@link AsyncCloseable}s and the current composite set in the order they are passed to this method. If
     * an order of closure is not required, then use {@link #mergeAll(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable appendAll(Iterable<? extends AsyncCloseable> asyncCloseables);


    /**
     * Prepends the passed {@link AsyncCloseable} with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of the previously registered {@link AsyncCloseable}s are closed too. This
     * method subscribes to the passed in {@link AsyncCloseable} and the current composite set in reverse order.
     * If an order of closure is not required, then use {@link #merge(AsyncCloseable)}.
     *
     * @param <T> the type of {@link AsyncCloseable} to be prepended
     * @param closeable {@link AsyncCloseable} that is closed when this {@link CompositeCloseable} is closed.
     * @return {@code T}.
     */
    <T extends AsyncCloseable> T prepend(T closeable);

    /**
     * Prepends all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too. This method subscribes to all
     * passed in {@link AsyncCloseable}s and the current composite set in the reverse order they are passed to this
     * method. If an order of closure is not required, then use {@link #mergeAll(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable prependAll(AsyncCloseable... asyncCloseables);

    /**
     * Prepends all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this {@link
     * CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too. This method subscribes to all
     * passed in {@link AsyncCloseable}s and the current composite set in the reverse order they are passed to this
     * method. If an order of closure is not required, then use {@link #mergeAll(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable prependAll(Iterable<? extends AsyncCloseable> asyncCloseables);

    /**
     * Closes all contained {@link AsyncCloseable}s. If any of the contained {@link AsyncCloseable}s terminated with a
     * failure, the returned {@link Completable} terminates with a failure only after all the contained {@link
     * AsyncCloseable}s have terminated.
     *
     * @return the {@link Completable} that is notified once the close is complete.
     */
    @Override
    Completable closeAsync();

    /**
     * Closes all contained {@link AsyncCloseable}s and awaits termination of all of them. If any of the contained
     * {@link AsyncCloseable}s terminated with a failure, this method terminates with an {@link IOException} only after
     * all the contained {@link AsyncCloseable}s have terminated.
     *
     * @throws IOException If any of the contained {@link AsyncCloseable}s terminate with a failure.
     */
    @Override
    void close() throws IOException;
}
