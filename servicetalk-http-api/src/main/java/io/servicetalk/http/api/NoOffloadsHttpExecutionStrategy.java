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

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.flatten;

final class NoOffloadsHttpExecutionStrategy implements HttpExecutionStrategy {

    static final HttpExecutionStrategy NO_OFFLOADS = new NoOffloadsHttpExecutionStrategy();

    private NoOffloadsHttpExecutionStrategy() {
        // Singleton
    }

    @Override
    public Single<StreamingHttpResponse> invokeClient(
            final Executor fallback, final StreamingHttpRequest request,
            final Function<Publisher<Object>, Single<StreamingHttpResponse>> client) {
        Publisher<Object> flatReq = flatten(request, request.payloadBodyAndTrailers()).subscribeOnOverride(immediate());
        return client.apply(flatReq)
                .map(response -> response.transformPayloadBody(p -> p.publishOnOverride(immediate())))
                .publishOnOverride(immediate());
    }

    @Override
    public Publisher<Object> invokeService(
            final Executor fallback, StreamingHttpRequest request,
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> service,
            final BiFunction<Throwable, Executor, Single<StreamingHttpResponse>> errorHandler) {
        request = request.transformPayloadBody(payload -> payload.publishOnOverride(immediate()));
        return service.apply(request)
                .onErrorResume(t -> errorHandler.apply(t, immediate()))
                .flatMapPublisher(response -> flatten(response, response.payloadBodyAndTrailers()))
                .subscribeOnOverride(immediate());
    }

    @Override
    public StreamingHttpService offloadService(final Executor fallback, final StreamingHttpRequestHandler handler) {
        return new StreamingHttpService() {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                HttpServiceContext wrappedCtx = new ExecutionContextOverridingServiceContext(ctx, immediate());
                request = request.transformPayloadBody(p -> p.publishOnOverride(immediate()));
                return handler.handle(wrappedCtx, request, responseFactory)
                        .map(r -> r.transformPayloadBody(p -> p.subscribeOnOverride(immediate())))
                        .subscribeOnOverride(immediate());
            }

            @Override
            public HttpExecutionStrategy executionStrategy() {
                return NO_OFFLOADS;
            }
        };
    }

    @Override
    public <T> Single<T> invokeService(Executor fallback, final Function<Executor, T> service) {
        return new FunctionToSingle<>(service, immediate());
    }

    @Override
    public <T> Single<T> offloadSend(final Executor fallback, final Single<T> original) {
        return original.subscribeOnOverride(immediate());
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor fallback, final Single<T> original) {
        return original.publishOnOverride(immediate());
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return original.subscribeOnOverride(immediate());
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return original.publishOnOverride(immediate());
    }

    @Override
    public Executor executor() {
        return immediate();
    }
}
