/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy;

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy.Merge;
import static io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy.ReturnOther;
import static io.servicetalk.http.api.HttpExecutionStrategies.difference;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * Default implementation for {@link HttpExecutionStrategy}.
 */
final class DefaultHttpExecutionStrategy implements HttpExecutionStrategy {

    static final byte OFFLOAD_RECEIVE_META = 1;
    static final byte OFFLOAD_RECEIVE_DATA = 2;
    static final byte OFFLOAD_SEND = 4;
    @Nullable
    private final Executor executor;
    private final byte offloads;
    private final MergeStrategy mergeStrategy;

    DefaultHttpExecutionStrategy(@Nullable final Executor executor, final byte offloads,
                                 final MergeStrategy mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
        this.executor = executor;
        this.offloads = offloads;
    }

    DefaultHttpExecutionStrategy(byte offloadOverride, HttpExecutionStrategy original) {
        offloads = offloadOverride;
        executor = original.executor();
        if (original instanceof DefaultHttpExecutionStrategy) {
            DefaultHttpExecutionStrategy originalAsDefault = (DefaultHttpExecutionStrategy) original;
            mergeStrategy = originalAsDefault.mergeStrategy;
        } else {
            mergeStrategy = Merge;
        }
    }

    @Nullable
    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public <FS> Single<StreamingHttpResponse> invokeClient(
            final Executor fallback, Publisher<Object> flattenedRequest, @Nullable final FS flushStrategy,
            final ClientInvoker<FS> client) {
        final Executor e = executor(fallback);
        if (offloaded(OFFLOAD_SEND)) {
            flattenedRequest = flattenedRequest.subscribeOn(e);
        }
        Single<StreamingHttpResponse> resp = client.invokeClient(flattenedRequest, flushStrategy);
        if (offloaded(OFFLOAD_RECEIVE_META)) {
            resp = resp.publishOn(e);
        }
        if (offloaded(OFFLOAD_RECEIVE_DATA)) {
            resp = resp.map(response -> response.transformMessageBody(payload -> payload.publishOn(e)));
        }
        return resp;
    }

    @Override
    public StreamingHttpService offloadService(final Executor fallback, final StreamingHttpService service) {
        return new StreamingHttpService() {
            private final Executor e = executor(fallback);

            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                // We compute the difference between the ExecutionStrategy from the current ExecutionContext and this
                // ExecutionStrategy to understand if we need to offload more than we already offloaded:
                final HttpExecutionStrategy diff = difference(fallback, ctx.executionContext().executionStrategy(),
                        DefaultHttpExecutionStrategy.this);
                // The service should see this ExecutionStrategy inside the ExecutionContext:
                final HttpServiceContext wrappedCtx =
                        new ExecutionContextOverridingServiceContext(ctx, DefaultHttpExecutionStrategy.this, e);
                if (diff == null) {
                    return service.handle(wrappedCtx, request, responseFactory);
                } else {
                    if (diff.isDataReceiveOffloaded()) {
                        request = request.transformMessageBody(p -> p.publishOn(e));
                    }
                    final Single<StreamingHttpResponse> resp;
                    if (diff.isMetadataReceiveOffloaded()) {
                        final StreamingHttpRequest r = request;
                        resp = e.submit(() -> service.handle(wrappedCtx, r, responseFactory).subscribeShareContext())
                                // exec.submit() returns a Single<Single<response>>, so flatten the nested Single.
                                .flatMap(identity());
                    } else {
                        resp = service.handle(wrappedCtx, request, responseFactory);
                    }
                    return diff.isSendOffloaded() ?
                            // This is different as compared to invokeService() where we just offload once on the
                            // flattened (meta + data) stream. In this case, we need to preserve the service contract
                            // and hence have to offload both meta and data separately.
                            resp.map(r -> r.transformMessageBody(p -> p.subscribeOn(e))).subscribeOn(e) : resp;
                }
            }

            @Override
            public Completable closeAsync() {
                return service.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return service.closeAsyncGracefully();
            }
        };
    }

    @Override
    public boolean isMetadataReceiveOffloaded() {
        return offloaded(OFFLOAD_RECEIVE_META);
    }

    @Override
    public boolean isDataReceiveOffloaded() {
        return offloaded(OFFLOAD_RECEIVE_DATA);
    }

    @Override
    public boolean isSendOffloaded() {
        return offloaded(OFFLOAD_SEND);
    }

    @Override
    public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
        if (equals(other)) {
            return this;
        }

        switch (mergeStrategy) {
            case ReturnSelf:
                return this;
            case ReturnOther:
                // If this strategy has an executor then we should preserve the executor even if we are returning other,
                // as the executors are provided by the user and our default behavior of custom merging shouldn't omit
                // the executor.
                if (executor == null || executor == other.executor()) {
                    return other;
                } else if (other instanceof DefaultHttpExecutionStrategy) {
                    DefaultHttpExecutionStrategy otherAsDefault = (DefaultHttpExecutionStrategy) other;
                    return new DefaultHttpExecutionStrategy(executor, otherAsDefault.offloads,
                            otherAsDefault.mergeStrategy);
                } else {
                    return new DefaultHttpExecutionStrategy(executor, generateOffloadsFlag(other),
                            Merge);
                }
            case Merge:
                break;
            default:
                throw new AssertionError("Unknown merge strategy: " + mergeStrategy);
        }

        if (other instanceof NoOffloadsHttpExecutionStrategy) {
            return other;
        }

        final Executor otherExecutor = other.executor();
        final Executor executor = otherExecutor == null ? this.executor : otherExecutor;
        if (other instanceof DefaultHttpExecutionStrategy) {
            DefaultHttpExecutionStrategy otherAsDefault = (DefaultHttpExecutionStrategy) other;
            if (otherAsDefault.mergeStrategy == ReturnOther) {
                // If other strategy just returns the mergeWith strategy, then no point in merging here.
                // return this;
                return this.executor == otherExecutor ? this :
                        new DefaultHttpExecutionStrategy(executor, offloads, mergeStrategy);
            }
            // We checked above that the two strategies are not equal, so just merge and return.
            return new DefaultHttpExecutionStrategy(executor, (byte) (otherAsDefault.offloads | offloads),
                    // Conservatively always merge if the two merge strategies are not equal
                    otherAsDefault.mergeStrategy == mergeStrategy ? mergeStrategy : Merge);
        }

        final byte otherOffloads;
        final MergeStrategy otherMergeStrategy;
        otherOffloads = generateOffloadsFlag(other);
        otherMergeStrategy = Merge; // always default to merge

        return (otherOffloads == offloads && executor == otherExecutor &&
                otherMergeStrategy == mergeStrategy) ? this :
                new DefaultHttpExecutionStrategy(executor, (byte) (otherOffloads | offloads), otherMergeStrategy);
    }

    private static byte generateOffloadsFlag(final HttpExecutionStrategy strategy) {
        return (byte) ((strategy.isDataReceiveOffloaded() ? OFFLOAD_RECEIVE_DATA : 0) |
                (strategy.isMetadataReceiveOffloaded() ? OFFLOAD_RECEIVE_META : 0) |
                (strategy.isSendOffloaded() ? OFFLOAD_SEND : 0));
    }

    @Override
    public <T> Single<T> invokeService(final Executor fallback, final Function<Executor, T> service) {
        final Executor e = executor(fallback);
        if (offloaded(OFFLOAD_RECEIVE_META)) {
            return e.submit(() -> service.apply(e));
        }
        return new FunctionToSingle<>(service, e);
    }

    @Override
    public <T> Single<T> offloadSend(final Executor fallback, final Single<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.subscribeOn(executor(fallback)) : original;
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor fallback, final Single<T> original) {
        return offloaded(OFFLOAD_RECEIVE_META) || offloaded(OFFLOAD_RECEIVE_DATA) ?
                original.publishOn(executor(fallback)) : original;
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.subscribeOn(executor(fallback)) : original;
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return offloaded(OFFLOAD_RECEIVE_META) || offloaded(OFFLOAD_RECEIVE_DATA) ?
                original.publishOn(executor(fallback)) : original;
    }

    private Executor executor(final Executor fallback) {
        requireNonNull(fallback);
        return executor == null ? fallback : executor;
    }

    // Visible for testing
    boolean offloaded(byte flag) {
        return (offloads & flag) == flag;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultHttpExecutionStrategy)) {
            return false;
        }

        final DefaultHttpExecutionStrategy that = (DefaultHttpExecutionStrategy) o;

        return offloads == that.offloads &&
                Objects.equals(executor, that.executor) &&
                mergeStrategy == that.mergeStrategy;
    }

    @Override
    public int hashCode() {
        int result = executor != null ? executor.hashCode() : 0;
        result = 31 * result + offloads;
        result = 31 * result + mergeStrategy.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "executor=" + executor +
                ", offloads=" + offloads +
                ", mergeStrategy=" + mergeStrategy +
                '}';
    }
}
