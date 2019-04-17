/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.SignalOffloaderFactory;
import io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy;

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor.ensureThreadAffinity;
import static io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy.Merge;
import static io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy.ReturnOther;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * Default implementation for {@link HttpExecutionStrategy}.
 */
class DefaultHttpExecutionStrategy implements HttpExecutionStrategy {

    static final byte OFFLOAD_RECEIVE_META = 1;
    static final byte OFFLOAD_RECEIVE_DATA = 2;
    static final byte OFFLOAD_SEND = 4;
    @Nullable
    private final Executor executor;
    private final byte offloads;
    private final MergeStrategy mergeStrategy;
    private final boolean threadAffinity;

    DefaultHttpExecutionStrategy(@Nullable final Executor executor, final byte offloads, final boolean threadAffinity,
                                 final MergeStrategy mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
        this.executor = executor != null ? threadAffinity ? ensureThreadAffinity(executor) : executor : null;
        this.offloads = offloads;
        this.threadAffinity = threadAffinity;
    }

    @Nullable
    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public Single<StreamingHttpResponse> invokeClient(
            final Executor fallback, final StreamingHttpRequest request,
            final Function<Publisher<Object>, Single<StreamingHttpResponse>> client) {
        final Executor e = executor(fallback);
        Publisher<Object> flatReq = flatten(request, request.payloadBodyAndTrailers());
        if (offloaded(OFFLOAD_SEND)) {
            flatReq = flatReq.subscribeOn(e);
        }
        Single<StreamingHttpResponse> resp = client.apply(flatReq);
        if (offloaded(OFFLOAD_RECEIVE_META)) {
            resp = resp.publishOn(e);
        }
        if (offloaded(OFFLOAD_RECEIVE_DATA)) {
            resp = resp.map(response -> response.transformRawPayloadBody(payload -> payload.publishOn(e)));
        }
        return resp;
    }

    @Override
    public Publisher<Object> invokeService(
            final Executor fallback, StreamingHttpRequest request,
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> service,
            final BiFunction<Throwable, Executor, Single<StreamingHttpResponse>> errorHandler) {
        final Executor e = executor(fallback);
        if (offloaded(OFFLOAD_RECEIVE_DATA)) {
            request = request.transformRawPayloadBody(payload -> payload.publishOn(e));
        }
        final Single<StreamingHttpResponse> responseSingle;
        if (offloaded(OFFLOAD_RECEIVE_META)) {
            final StreamingHttpRequest r = request;
            responseSingle = e.submit(() -> service.apply(r).subscribeShareContext())
                    // exec.submit() returns a Single<Single<response>>, so flatten the nested Single.
                    .flatMap(identity());
        } else {
            responseSingle = service.apply(request);
        }
        Publisher<Object> resp = responseSingle.recoverWith(t -> errorHandler.apply(t, e))
                .flatMapPublisher(response -> flatten(response, response.payloadBodyAndTrailers()));
        if (offloaded(OFFLOAD_SEND)) {
            resp = resp.subscribeOn(e);
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
                HttpServiceContext wrappedCtx =
                        new ExecutionContextOverridingServiceContext(ctx, DefaultHttpExecutionStrategy.this, e);
                if (offloaded(OFFLOAD_RECEIVE_DATA)) {
                    request = request.transformRawPayloadBody(p -> p.publishOn(e));
                }
                final Single<StreamingHttpResponse> resp;
                if (offloaded(OFFLOAD_RECEIVE_META)) {
                    final StreamingHttpRequest r = request;
                    resp = e.submit(() -> service.handle(wrappedCtx, r, responseFactory).subscribeShareContext())
                            // exec.submit() returns a Single<Single<response>>, so flatten the nested Single.
                            .flatMap(identity());
                } else {
                    resp = service.handle(wrappedCtx, request, responseFactory);
                }
                return offloaded(OFFLOAD_SEND) ?
                        // This is different as compared to invokeService() where we just offload once on the
                        // flattened (meta + data) stream. In this case, we need to preserve the service contract and
                        // hence have to offload both meta and data separately.
                        resp.map(r -> r.transformRawPayloadBody(p -> p.subscribeOn(e))).subscribeOn(e) : resp;
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
                            otherAsDefault.threadAffinity, otherAsDefault.mergeStrategy);
                } else {
                    return new DefaultHttpExecutionStrategy(executor, generateOffloadsFlag(other),
                            extractThreadAffinity(other.executor()), Merge);
                }
            case Merge:
                break;
            default:
                throw new AssertionError("Unknown merge strategy: " + mergeStrategy);
        }

        if (other instanceof NoOffloadsHttpExecutionStrategy) {
            return this;
        }

        final Executor otherExecutor = other.executor();
        final Executor executor = otherExecutor == null ? this.executor : otherExecutor;
        if (other instanceof DefaultHttpExecutionStrategy) {
            DefaultHttpExecutionStrategy otherAsDefault = (DefaultHttpExecutionStrategy) other;
            if (otherAsDefault.mergeStrategy == ReturnOther) {
                // If other strategy just returns the mergeWith strategy, then no point in merging here.
                // return this;
                return this.executor == otherExecutor ? this :
                        new DefaultHttpExecutionStrategy(executor, offloads, threadAffinity, mergeStrategy);
            }
            // We checked above that the two strategies are not equal, so just merge and return.
            return new DefaultHttpExecutionStrategy(executor, (byte) (otherAsDefault.offloads | offloads),
                    threadAffinity || otherAsDefault.threadAffinity,
                    // Conservatively always merge if the two merge strategies are not equal
                    otherAsDefault.mergeStrategy == mergeStrategy ? mergeStrategy : Merge);
        }

        final byte otherOffloads;
        final boolean otherThreadAffinity;
        final MergeStrategy otherMergeStrategy;
        otherOffloads = generateOffloadsFlag(other);
        otherThreadAffinity = extractThreadAffinity(otherExecutor);
        otherMergeStrategy = Merge; // always default to merge

        return (otherOffloads == offloads && executor == otherExecutor && otherThreadAffinity == threadAffinity &&
                otherMergeStrategy == mergeStrategy) ? this :
                new DefaultHttpExecutionStrategy(executor, (byte) (otherOffloads | offloads),
                        threadAffinity || otherThreadAffinity, otherMergeStrategy);
    }

    private boolean extractThreadAffinity(@Nullable final Executor otherExecutor) {
        return otherExecutor instanceof SignalOffloaderFactory &&
                ((SignalOffloaderFactory) otherExecutor).hasThreadAffinity();
    }

    private byte generateOffloadsFlag(final HttpExecutionStrategy strategy) {
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
        return executor == null ? threadAffinity ? ensureThreadAffinity(fallback) : fallback : executor;
    }

    // Visible for testing
    boolean hasThreadAffinity() {
        return threadAffinity;
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultHttpExecutionStrategy that = (DefaultHttpExecutionStrategy) o;

        if (offloads != that.offloads) {
            return false;
        }
        if (threadAffinity != that.threadAffinity) {
            return false;
        }
        if (executor != null ? !executor.equals(that.executor) : that.executor != null) {
            return false;
        }
        return mergeStrategy == that.mergeStrategy;
    }

    @Override
    public int hashCode() {
        int result = executor != null ? executor.hashCode() : 0;
        result = 31 * result + (int) offloads;
        result = 31 * result + mergeStrategy.hashCode();
        result = 31 * result + (threadAffinity ? 1 : 0);
        return result;
    }

    static Publisher<Object> flatten(HttpMetaData metaData, Publisher<Object> payload) {
        return (Publisher.<Object>from(metaData)).concat(payload);
    }
}
