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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

final class DefaultCompositeCloseable implements CompositeCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompositeCloseable.class);

    private Completable closeAsync = completed();

    @Override
    public CompositeCloseable merge(final AsyncCloseable... asyncCloseables) {
        return merge(stream(asyncCloseables));
    }

    @Override
    public CompositeCloseable merge(Iterable<? extends AsyncCloseable> asyncCloseables) {
        return merge(StreamSupport.stream(asyncCloseables.spliterator(), false));
    }

    @Override
    public CompositeCloseable concat(final AsyncCloseable... asyncCloseables) {
        return concat(stream(asyncCloseables));
    }

    @Override
    public CompositeCloseable concat(final Iterable<? extends AsyncCloseable> asyncCloseables) {
        return concat(StreamSupport.stream(asyncCloseables.spliterator(), false));
    }

    @Override
    public Completable closeAsync() {
        return closeAsync;
    }

    @Override
    public void close() throws IOException {
        try {
            awaitIndefinitely(closeAsync());
        } catch (ExecutionException | InterruptedException e) {
            throw new IOException(e);
        }
    }

    private CompositeCloseable merge(final Stream<? extends AsyncCloseable> closeables) {
        closeAsync = closeAsync.mergeDelayError(closeables.map(AsyncCloseable::closeAsync).collect(toList()));
        return this;
    }

    private CompositeCloseable concat(final Stream<? extends AsyncCloseable> closeables) {
        closeables.forEach(closeable -> closeAsync = closeAsync.andThen(closeable.closeAsync().onErrorResume(th -> {
            //TODO: This should use concatDelayeError when available.
            LOGGER.debug("Ignored failed to close {}.", closeable, th);
            return completed();
        })));
        return this;
    }
}
