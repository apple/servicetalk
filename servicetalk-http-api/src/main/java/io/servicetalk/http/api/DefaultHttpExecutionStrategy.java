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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * Default implementation for {@link HttpExecutionStrategy}.
 */
final class DefaultHttpExecutionStrategy implements HttpExecutionStrategy {

    static final byte OFFLOAD_RECEIVE_META = 2;
    static final byte OFFLOAD_RECEIVE_DATA = 4;
    static final byte OFFLOAD_SEND = 8;
    @Nullable
    private final Executor executor;
    private final byte offloads;

    DefaultHttpExecutionStrategy(@Nullable final Executor executor, final byte offloads) {
        this.executor = executor;
        this.offloads = offloads;
    }

    @Nullable
    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public Single<StreamingHttpResponse> invokeClient(final Executor fallback, final StreamingHttpRequest request,
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
            resp = resp.map(response -> response.transformPayloadBody(payload -> payload.publishOn(e)));
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
            request = request.transformPayloadBody(payload -> payload.publishOn(e));
        }
        Single<StreamingHttpResponse> responseSingle;
        if (offloaded(OFFLOAD_RECEIVE_META)) {
            final StreamingHttpRequest r = request;
            responseSingle = e.submit(() -> service.apply(r))
                    // exec.submit() returns a Single<Single<response>>, so flatten the nested Single.
                    .flatMap(identity());
        } else {
            responseSingle = service.apply(request);
        }
        Publisher<Object> resp = responseSingle.onErrorResume(t -> errorHandler.apply(t, e))
                .flatMapPublisher(response -> flatten(response, response.payloadBodyAndTrailers()));
        if (offloaded(OFFLOAD_SEND)) {
            resp = resp.subscribeOn(e);
        }
        return resp;
    }

    @Override
    public StreamingHttpService wrap(final Executor fallback, final StreamingHttpRequestHandler handler) {
        final Executor e = executor(fallback);
        return new StreamingHttpService() {

            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                HttpServiceContext wrappedCtx = new ExecutionContextOverridingServiceContext(ctx, e);
                if (offloaded(OFFLOAD_RECEIVE_DATA)) {
                    request = request.transformPayloadBody(p -> p.publishOn(e));
                }
                Single<StreamingHttpResponse> resp;
                if (offloaded(OFFLOAD_RECEIVE_META)) {
                    final StreamingHttpRequest r = request;
                    resp = e.submit(() -> handler.handle(wrappedCtx, r, responseFactory))
                            // exec.submit() returns a Single<Single<response>>, so flatten the nested Single.
                            .flatMap(identity());
                } else {
                    resp = handler.handle(wrappedCtx, request, responseFactory);
                }
                return offloaded(OFFLOAD_SEND) ?
                        resp.map(r -> r.transformPayloadBody(p -> p.subscribeOn(e))).subscribeOn(e) : resp;
            }

            @Override
            public HttpExecutionStrategy executionStrategy() {
                return DefaultHttpExecutionStrategy.this;
            }
        };
    }

    @Override
    public <T> Single<T> invokeService(final Executor fallback, final Function<Executor, T> service) {
        Executor e = executor(fallback);
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
        return offloaded(OFFLOAD_SEND) ? original.publishOn(executor(fallback)) : original;
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.subscribeOn(executor(fallback)) : original;
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.publishOn(executor(fallback)) : original;
    }

    private Executor executor(final Executor fallback) {
        requireNonNull(fallback);
        return executor == null ? fallback : executor;
    }

    private boolean offloaded(byte flag) {
        return (offloads & flag) == flag;
    }

    static Publisher<Object> flatten(HttpMetaData metaData, Publisher<Object> payload) {
        return (Publisher.<Object>just(metaData)).concatWith(payload);
    }
}
