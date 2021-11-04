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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;

import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static java.util.function.Function.identity;

/**
 * Wraps a {@link StreamingHttpService} to apply the provided {@link HttpExecutionStrategy} and {@link Executor} for
 * offloading.
 */
public class StreamingHttpServiceToOffloadedStreamingHttpService implements StreamingHttpService {

    private final StreamingHttpService delegate;
    @Nullable
    private final Executor executor;
    private final BooleanSupplier shouldOffload;
    private final HttpExecutionStrategy strategy;

    StreamingHttpServiceToOffloadedStreamingHttpService(final HttpExecutionStrategy strategy,
                                                        @Nullable final Executor executor,
                                                        final BooleanSupplier shouldOffload,
                                                        final StreamingHttpService delegate) {
        this.strategy = strategy;
        this.executor = executor;
        this.shouldOffload = shouldOffload;
        this.delegate = delegate;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        // We compute the difference between the ExecutionStrategy from the current ExecutionContext and
        // this ExecutionStrategy to understand if we need to offload more than we already offloaded:
        final HttpExecutionStrategy additionalOffloads = ctx.executionContext().executionStrategy().missing(strategy);

        Executor useExecutor = null != executor ? executor : ctx.executionContext().executor();

        // The service should see this ExecutionStrategy and Executor inside the ExecutionContext:
        final HttpServiceContext wrappedCtx =
                new ExecutionContextOverridingServiceContext(ctx, strategy, useExecutor);

        if (!additionalOffloads.hasOffloads()) {
            // No additional offloading needed.
            return delegate.handle(wrappedCtx, request, responseFactory);
        } else {
            if (additionalOffloads.isDataReceiveOffloaded()) {
                request = request.transformMessageBody(p ->
                        p.publishOn(useExecutor, shouldOffload));
            }
            final Single<StreamingHttpResponse> resp;
            if (additionalOffloads.isMetadataReceiveOffloaded() && shouldOffload.getAsBoolean()) {
                final StreamingHttpRequest r = request;
                resp = useExecutor.submit(
                                () -> delegate.handle(wrappedCtx, r, responseFactory).subscribeShareContext())
                        // exec.submit() returns a Single<Single<response>>, so flatten nested Single.
                        .flatMap(identity());
            } else {
                resp = delegate.handle(wrappedCtx, request, responseFactory);
            }
            return additionalOffloads.isSendOffloaded() ?
                    // This is different as compared to invokeService() where we just offload once on
                    // the flattened (meta + data) stream. In this case, we need to preserve the service
                    // contract and hence have to offload both meta and data separately.
                    resp.map(r -> r.transformMessageBody(p ->
                                    p.subscribeOn(useExecutor, shouldOffload)))
                            .subscribeOn(useExecutor, shouldOffload) :
                    resp;
        }
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    /**
     * Wraps the passed {@link StreamingHttpServiceToOffloadedStreamingHttpService} to apply the provided
     * {@link HttpExecutionStrategy} for offloading.
     *
     * @param strategy {@link HttpExecutionStrategy} to use for offloading.
     * @param executor {@link Executor} to use as executor or {@code null} to use Execution context executor.
     * @param shouldOffload If {@link BooleanSupplier} returns {@code true} then offload to executor otherwise continue
     * execution on calling thread.
     * @param service {@link StreamingHttpServiceToOffloadedStreamingHttpService} to wrap.
     * @return Wrapped {@link StreamingHttpServiceToOffloadedStreamingHttpService}.
     */
    public static StreamingHttpService offloadService(final HttpExecutionStrategy strategy,
                                                      @Nullable final Executor executor,
                                                      final BooleanSupplier shouldOffload,
                                                      final StreamingHttpService service) {
        return strategy.hasOffloads() ?
                new StreamingHttpServiceToOffloadedStreamingHttpService(strategy, executor, shouldOffload, service) :
                new StreamingHttpService() {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        Executor useExecutor = null != executor ? executor : ctx.executionContext().executor();

                        // The service should see this ExecutionStrategy and Executor inside the ExecutionContext:
                        HttpServiceContext wrappedCtx =
                                new ExecutionContextOverridingServiceContext(ctx, strategy, useExecutor);

                        return service.handle(wrappedCtx, request, responseFactory);
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
}
