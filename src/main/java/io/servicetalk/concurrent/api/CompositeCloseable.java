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

import java.io.Closeable;

/**
 * A {@link AsyncCloseable} and {@link Closeable} that allows for adding new {@link AsyncCloseable}s till it is
 * closed.
 */
public interface CompositeCloseable extends AsyncCloseable, AutoCloseable {

    /**
     * Merges all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this
     * {@link CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too.
     * This method subscribes to all {@link AsyncCloseable#closeAsync()} at the same time. If an order of closure is
     * required, then use {@link #concat(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable merge(AsyncCloseable... asyncCloseables);

    /**
     * Merges all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this
     * {@link CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too.
     * This method subscribes to all {@link AsyncCloseable#closeAsync()} at the same time. If an order of closure is
     * required, then use {@link #concat(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable merge(Iterable<? extends AsyncCloseable> asyncCloseables);

    /**
     * Concats all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this
     * {@link CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too.
     * This method subscribes to all {@link AsyncCloseable#closeAsync()} in the order they are passed to this
     * method. If an order of closure is not required, then use {@link #merge(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable concat(AsyncCloseable... asyncCloseables);

    /**
     * Concats all the passed {@link AsyncCloseable}s with this {@link CompositeCloseable} such that when this
     * {@link CompositeCloseable} is closed, all of these {@link AsyncCloseable}s are closed too.
     * This method subscribes to all {@link AsyncCloseable#closeAsync()} in the order they are passed to this
     * method. If an order of closure is not required, then use {@link #merge(AsyncCloseable...)}.
     *
     * @param asyncCloseables All {@link AsyncCloseable}s that are closed when this {@link CompositeCloseable} is
     * closed.
     * @return {@code this}.
     */
    CompositeCloseable concat(Iterable<? extends AsyncCloseable> asyncCloseables);

    /**
     * Closes all contained {@link AsyncCloseable}s. If any of the contained
     * {@link AsyncCloseable}s terminated with a failure, the returned {@link Completable} terminates with a failure
     * only after all the contained {@link AsyncCloseable}s have terminated.
     *
     * @return the {@link Completable} that is notified once the close is complete.
     */
    @Override
    Completable closeAsync();

    /**
     * Closes all contained {@link AsyncCloseable}s and awaits termination of all of them. If any of the contained
     * {@link AsyncCloseable}s terminated with a failure, this method terminates with an {@link Exception} only
     * after all the contained {@link AsyncCloseable}s have terminated.
     *
     * @throws Exception If any of the contained {@link AsyncCloseable}s terminate with a failure.
     */
    @Override
    void close() throws Exception;
}
