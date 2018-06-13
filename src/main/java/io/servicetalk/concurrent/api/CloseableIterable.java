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

import java.util.Iterator;

/**
 * An {@link Iterable} which supports generation of {@link CloseableIterator}s.
 * <p>
 *     This interface is meant to be used in places where an {@link Iterator} returned by an {@link Iterable} contains
 *     state that is required to be cleared irrespective of whether data from the {@link Iterator} is completely
 *     consumed (i.e. {@link Iterator#hasNext()} is called till it returns {@code false}) or not.
 *     This interface provides a way for a user of such an {@link Iterable} to discard data, by calling
 *     {@link CloseableIterator#close()}.
 *     When using {@link CloseableIterator}, it is expected that the user will either consume all the data from the
 *     {@link Iterator} or explicitly call {@link AutoCloseable#close()} to dispose the remaining data.
 *
 * @param <T> the type of elements returned by the {@link CloseableIterator}.
 */
public interface CloseableIterable<T> extends Iterable<T> {

    @Override
    CloseableIterator<T> iterator();
}
