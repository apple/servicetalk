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

import java.util.List;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

final class DefaultCompositeCloseable implements CompositeCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompositeCloseable.class);

    private Completable closeAsync = completed();
    private Completable closeAsyncGracefully = completed();

    @Override
    public <T extends AsyncCloseable> T merge(final T closeable) {
        mergeCloseableDelayError(closeable);
        return closeable;
    }

    @Override
    public CompositeCloseable mergeAll(final AsyncCloseable... asyncCloseables) {
        mergeCloseableDelayError(asList(asyncCloseables));
        return this;
    }

    @Override
    public CompositeCloseable mergeAll(Iterable<? extends AsyncCloseable> asyncCloseables) {
        mergeCloseableDelayError(stream(asyncCloseables.spliterator(), false).collect(toList()));
        return this;
    }

    @Override
    public <T extends AsyncCloseable> T append(final T closeable) {
        concatCloseableDelayError(closeable);
        return closeable;
    }

    @Override
    public CompositeCloseable appendAll(final AsyncCloseable... asyncCloseables) {
        for (AsyncCloseable closeable : asyncCloseables) {
            concatCloseableDelayError(closeable);
        }
        return this;
    }

    @Override
    public CompositeCloseable appendAll(final Iterable<? extends AsyncCloseable> asyncCloseables) {
        asyncCloseables.forEach(this::concatCloseableDelayError);
        return this;
    }

    @Override
    public <T extends AsyncCloseable> T prepend(final T closeable) {
        prependCloseableDelayError(closeable);
        return closeable;
    }

    @Override
    public CompositeCloseable prependAll(final AsyncCloseable... asyncCloseables) {
        for (AsyncCloseable closeable : asyncCloseables) {
            prependCloseableDelayError(closeable);
        }
        return this;
    }

    @Override
    public CompositeCloseable prependAll(final Iterable<? extends AsyncCloseable> asyncCloseables) {
        asyncCloseables.forEach(this::prependCloseableDelayError);
        return this;
    }

    @Override
    public Completable closeAsync() {
        return closeAsync;
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeAsyncGracefully;
    }

    @Override
    public void close() {
        awaitTermination(closeAsync().toFuture());
    }

    @Override
    public void closeGracefully() {
        awaitTermination(closeAsyncGracefully().toFuture());
    }

    private void mergeCloseableDelayError(final AsyncCloseable closeable) {
        closeAsync = closeAsync.mergeDelayError(closeable.closeAsync());
        closeAsyncGracefully = closeAsyncGracefully.mergeDelayError(closeable.closeAsyncGracefully());
    }

    private void mergeCloseableDelayError(final List<AsyncCloseable> closeables) {
        closeAsync = closeAsync.mergeDelayError(closeables.stream().map(AsyncCloseable::closeAsync).collect(toList()));
        closeAsyncGracefully = closeAsyncGracefully.mergeDelayError(
                closeables.stream().map(AsyncCloseable::closeAsyncGracefully).collect(toList()));
    }

    private void concatCloseableDelayError(final AsyncCloseable closeable) {
        closeAsync = closeAsync.concat(closeable.closeAsync().onErrorComplete(th -> {
            //TODO: This should use concatDelayError when available.
            LOGGER.debug("Ignored failure to close {}.", closeable, th);
            return true;
        }));
        closeAsyncGracefully = closeAsyncGracefully.concat(closeable.closeAsyncGracefully().onErrorComplete(th -> {
            //TODO: This should use concatDelayError when available.
            LOGGER.debug("Ignored failure to close {}.", closeable, th);
            return true;
        }));
    }

    private void prependCloseableDelayError(final AsyncCloseable closeable) {
        closeAsync = closeable.closeAsync().onErrorComplete(th -> {
            //TODO: This should use prependDelayError when available.
            LOGGER.debug("Ignored failure to close {}.", closeable, th);
            return true;
        }).concat(closeAsync);
        closeAsyncGracefully = closeable.closeAsyncGracefully().onErrorComplete(th -> {
            //TODO: This should use prependDelayError when available.
            LOGGER.debug("Ignored failure to close {}.", closeable, th);
            return true;
        }).concat(closeAsyncGracefully);
    }
}
