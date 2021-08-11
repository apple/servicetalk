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
import io.servicetalk.transport.api.ExecutionStrategy;

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * An execution strategy for HTTP client and servers.
 */
public interface HttpExecutionStrategy extends ExecutionStrategy {

    /**
     * Invokes the passed {@link Function} and applies the necessary offloading of request and response for a client.
     *
     * @param fallback {@link Executor} to use as a fallback if this {@link HttpExecutionStrategy} does not define an
     * {@link Executor}.
     * @param flattenedRequest A flattened {@link Publisher} containing all data constituting an HTTP request.
     * @param flushStrategy The {@code FlushStrategy} to use.
     * @param client A {@link BiFunction} that given flattened stream of {@link HttpRequestMetaData}, payload and
     * trailers, for the passed {@link StreamingHttpRequest} returns a {@link Single}.
     * @param <FS> The {@code FlushStrategy} type to use.
     * @return {@link Single} which is offloaded as required.
     */
    <FS> Single<StreamingHttpResponse> invokeClient(Executor fallback, Publisher<Object> flattenedRequest,
                                                    @Nullable FS flushStrategy, ClientInvoker<FS> client);

    /**
     * Invokes a service represented by the passed {@link Function}.
     * <p>
     * This method does not apply the strategy on the object returned from the {@link Function}, if that object is an
     * asynchronous source then the caller of this method should take care and offload that source using
     * {@link #offloadSend(Executor, Single)} or {@link #offloadSend(Executor, Publisher)}.
     *
     * @param <T> Type of result of the invocation of the service.
     * @param fallback {@link Executor} to use as a fallback if this {@link HttpExecutionStrategy} does not define an
     * {@link Executor}.
     * @param service {@link Function} representing a service.
     * @return A {@link Single} that invokes the passed {@link Function} and returns the result asynchronously.
     * Invocation of {@link Function} will be offloaded if configured.
     */
    <T> Single<T> invokeService(Executor fallback, Function<Executor, T> service);

    /**
     * Wraps the passed {@link StreamingHttpService} to apply this {@link HttpExecutionStrategy}.
     *
     * @param fallback {@link Executor} to use as a fallback if this {@link HttpExecutionStrategy} does not define an
     * {@link Executor}.
     * @param handler {@link StreamingHttpService} to wrap.
     * @return Wrapped {@link StreamingHttpService}.
     */
    StreamingHttpService offloadService(Executor fallback, StreamingHttpService handler);

    /**
     * Returns {@code true} if metadata receive offloading is enabled for this {@link ExecutionStrategy}.
     *
     * @return {@code true} if metadata receive offloading is enabled for this {@link ExecutionStrategy}.
     */
    boolean isMetadataReceiveOffloaded();

    /**
     * Returns {@code true} if data receive offloading is enabled for this {@link ExecutionStrategy}.
     *
     * @return {@code true} if data receive offloading is enabled for this {@link ExecutionStrategy}.
     */
    boolean isDataReceiveOffloaded();

    /**
     * Returns {@code true} if send offloading is enabled for this {@link ExecutionStrategy}.
     *
     * @return {@code true} if send offloading is enabled for this {@link ExecutionStrategy}.
     */
    boolean isSendOffloaded();

    /**
     * Merges the passed {@link HttpExecutionStrategy} with {@code this} {@link HttpExecutionStrategy} and return the
     * merged result.
     *
     * @param other {@link HttpExecutionStrategy} to merge with {@code this}.
     * @return Merged {@link HttpExecutionStrategy}.
     */
    HttpExecutionStrategy merge(HttpExecutionStrategy other);
}
