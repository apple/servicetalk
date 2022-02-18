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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.internal.FutureUtils.awaitTermination;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

final class DefaultCompositeCloseable implements CompositeCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompositeCloseable.class);

    /**
     * Initial size of 2 because we don't expect a large cardinality of {@link Operand}s. Usually only 1 is present
     * (e.g. either merge or concat).
     */
    private final Deque<Operand> operands = new ArrayDeque<>(2);
    @Nullable
    private Completable closeAsync;
    @Nullable
    private Completable closeAsyncGracefully;

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
        appendCloseableDelayError(closeable);
        return closeable;
    }

    @Override
    public CompositeCloseable appendAll(final AsyncCloseable... asyncCloseables) {
        for (AsyncCloseable closeable : asyncCloseables) {
            appendCloseableDelayError(closeable);
        }
        return this;
    }

    @Override
    public CompositeCloseable appendAll(final Iterable<? extends AsyncCloseable> asyncCloseables) {
        asyncCloseables.forEach(this::appendCloseableDelayError);
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
        if (closeAsync == null) {
            closeAsync = buildCompletable(AsyncCloseable::closeAsync);
        }
        return closeAsync;
    }

    @Override
    public Completable closeAsyncGracefully() {
        if (closeAsyncGracefully == null) {
            closeAsyncGracefully = buildCompletable(AsyncCloseable::closeAsyncGracefully);
        }
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
        final Operand operand = getOrAddMergeOperand();
        operand.closables.add(closeable);
        resetState();
    }

    private void mergeCloseableDelayError(final List<AsyncCloseable> closeables) {
        final Operand operand = getOrAddMergeOperand();
        operand.closables.addAll(closeables);
        resetState();
    }

    private void resetState() {
        closeAsync = closeAsyncGracefully = null;
    }

    private Operand getOrAddMergeOperand() {
        return getOrAddOperand(true, true);
    }

    private Operand getOrAddConcatOperand(boolean append) {
        return getOrAddOperand(append, false);
    }

    private Operand getOrAddOperand(boolean append, boolean isMerge) {
        final Operand operand;
        if (operands.isEmpty()) {
            operand = new Operand(isMerge);
            operands.add(operand);
        } else {
            Operand rawOperand = append ? operands.getLast() : operands.getFirst();
            if (isMerge == rawOperand.isMerge) {
                operand = rawOperand;
            } else {
                operand = new Operand(isMerge);
                if (append) {
                    operands.addLast(operand);
                } else {
                    operands.addFirst(operand);
                }
            }
        }
        return operand;
    }

    private void appendCloseableDelayError(final AsyncCloseable closeable) {
        final Operand operand = getOrAddConcatOperand(true);
        operand.closables.addLast(closeable);
        resetState();
    }

    private void prependCloseableDelayError(final AsyncCloseable closeable) {
        final Operand operand = getOrAddConcatOperand(false);
        operand.closables.addFirst(closeable);
        resetState();
    }

    private Completable buildCompletable(Function<AsyncCloseable, Completable> closerFunc) {
        Completable result = completed();
        for (Operand operand : operands) {
            if (operand.isMerge) {
                result = result.mergeDelayError(operand.closables.stream().map(closerFunc).toArray(Completable[]::new));
            } else {
                result = result.concat(operand.closables.stream().map(closerFunc).map(completable ->
                        completable.onErrorComplete(th -> {
                            // TODO: This should use concatDelayError when available.
                            LOGGER.debug("Ignored failure to close", th);
                            return true;
                        })).toArray(Completable[]::new));
            }
        }
        return result;
    }

    private static final class Operand {
        final Deque<AsyncCloseable> closables = new ArrayDeque<>(2);
        final boolean isMerge;

        Operand(boolean isMerge) {
            this.isMerge = isMerge;
        }
    }
}
