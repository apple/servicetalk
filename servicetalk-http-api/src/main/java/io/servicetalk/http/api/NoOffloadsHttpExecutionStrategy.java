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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static java.util.Objects.requireNonNull;

final class NoOffloadsHttpExecutionStrategy implements HttpExecutionStrategy {

    static final HttpExecutionStrategy NO_OFFLOADS_NO_EXECUTOR = new NoOffloadsHttpExecutionStrategy();

    @Nullable
    private final Executor executor;

    private NoOffloadsHttpExecutionStrategy() {
        // Using immediate() here isn't a desirable default as it may end up being used as the Executor for the
        // associated server and hence any tasks run on the Executor will not be offloaded which may not be the intent.
        // If a user does want to use immediate() for the server they can create a delegating strategy to do that.
        this.executor = null;
    }

    NoOffloadsHttpExecutionStrategy(final Executor executor) {
        this.executor = requireNonNull(executor);
    }

    @Override
    public <FS> Single<StreamingHttpResponse> invokeClient(final Executor fallback,
                                                           final Publisher<Object> flattenedRequest,
                                                           final FS flushStrategy, final ClientInvoker<FS> client) {
        return client.invokeClient(flattenedRequest, flushStrategy);
    }

    @Override
    public StreamingHttpService offloadService(final Executor fallback, final StreamingHttpService handler) {
        return (ctx, request, responseFactory) -> {
            // Always use fallback as the Executor as this strategy does not specify an Executor.
            HttpServiceContext wrappedCtx =
                    new ExecutionContextOverridingServiceContext(ctx, NoOffloadsHttpExecutionStrategy.this, fallback);
            return handler.handle(wrappedCtx, request, responseFactory);
        };
    }

    @Override
    public boolean isMetadataReceiveOffloaded() {
        return false;
    }

    @Override
    public boolean isDataReceiveOffloaded() {
        return false;
    }

    @Override
    public boolean isSendOffloaded() {
        return false;
    }

    @Override
    public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
        return this;
    }

    @Override
    public <T> Single<T> invokeService(final Executor fallback, final Function<Executor, T> service) {
        return new FunctionToSingle<>(service, immediate());
    }

    @Override
    public <T> Single<T> offloadSend(final Executor fallback, final Single<T> original) {
        return original;
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor fallback, final Single<T> original) {
        return original;
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return original;
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return original;
    }

    @Override
    public Executor executor() {
        return executor;
    }
}
