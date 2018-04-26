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

import java.util.Spliterator;

/**
 * An {@link Spliterator} which supports {@link AutoCloseable#close()}.
 * @param <T> the type of elements returned by this {@link Spliterator}.
 */
public interface BlockingSpliterator<T> extends Spliterator<T>, AutoCloseable {
    @Override
    BlockingSpliterator<T> trySplit();

    /**
     * This method is used to communicate that you are no longer interested in consuming data.
     * This provides a "best effort" notification to the producer of data that you are no longer interested in data
     * from this {@link Spliterator}.
     * <p>
     * If all the data has not been consumed this may have transport implications (e.g. if the source of data comes from
     * a socket or file descriptor). If all data has been consumed, or this {@link BlockingIterator} has previously
     * been closed this should be a noop.
     */
    @Override
    void close() throws Exception;
}
