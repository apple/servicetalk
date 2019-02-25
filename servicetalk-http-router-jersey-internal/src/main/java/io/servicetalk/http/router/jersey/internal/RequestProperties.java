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
package io.servicetalk.http.router.jersey.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.http.api.HttpExecutionStrategy;

import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.GenericType;

import static java.util.Objects.requireNonNull;

/**
 * Helper methods used internally for accessing ServiceTalk-specific request properties.
 */
public final class RequestProperties {
    private static final String REQUEST_BUFFER_PUBLISHER_IS =
            new GenericType<BufferPublisherInputStream>() { }.getType().getTypeName();

    private static final String REQUEST_CANCELLABLE =
            new GenericType<DelayedCancellable>() { }.getType().getTypeName();

    private static final String RESPONSE_BUFFER_PUBLISHER =
            new GenericType<Publisher<Buffer>>() { }.getType().getTypeName();

    private static final String RESPONSE_EXEC_STRATEGY =
            new GenericType<HttpExecutionStrategy>() { }.getType().getTypeName();

    private RequestProperties() {
        // no instances
    }

    /**
     * Initialize all request properties.
     *
     * @param entityStream the {@link BufferPublisherInputStream} associated with the request.
     * @param reqCtx the {@link ContainerRequestContext} for the request
     */
    public static void initRequestProperties(final BufferPublisherInputStream entityStream,
                                             final ContainerRequestContext reqCtx) {
        reqCtx.setProperty(REQUEST_BUFFER_PUBLISHER_IS, requireNonNull(entityStream));
        reqCtx.setProperty(REQUEST_CANCELLABLE, new DelayedCancellable());
        reqCtx.setProperty(RESPONSE_BUFFER_PUBLISHER, null);
        reqCtx.setProperty(RESPONSE_EXEC_STRATEGY, null);
    }

    /**
     * Get the {@link BufferPublisherInputStream} associated with the request.
     *
     * @param reqCtx the {@link ContainerRequestContext} for the request
     * @return the {@link BufferPublisherInputStream} associated with the request
     */
    public static BufferPublisherInputStream getRequestBufferPublisherInputStream(
            final ContainerRequestContext reqCtx) {
        return (BufferPublisherInputStream) reqCtx.getProperty(REQUEST_BUFFER_PUBLISHER_IS);
    }

    /**
     * Get the request {@link Cancellable}.
     *
     * @param reqCtx the {@link ContainerRequestContext} for the request
     * @return the request {@link Cancellable}.
     */
    public static Cancellable getRequestCancellable(final ContainerRequestContext reqCtx) {
        return (DelayedCancellable) reqCtx.getProperty(REQUEST_CANCELLABLE);
    }

    /**
     * Set the request {@link Cancellable}.
     *
     * @param cancellable request {@link Cancellable}.
     * @param reqCtx the {@link ContainerRequestContext} for the request
     */
    public static void setRequestCancellable(final Cancellable cancellable,
                                             final ContainerRequestContext reqCtx) {
        ((DelayedCancellable) reqCtx.getProperty(REQUEST_CANCELLABLE))
                .delayedCancellable(requireNonNull(cancellable));
    }

    /**
     * Get the response {@link Publisher Publisher&lt;Buffer&gt;}.
     *
     * @param reqCtx the {@link ContainerRequestContext} for the request
     * @return the response {@link Publisher Publisher&lt;Buffer&gt;} or {@code null} if none has been set.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Publisher<Buffer> getResponseBufferPublisher(final ContainerRequestContext reqCtx) {
        return (Publisher<Buffer>) reqCtx.getProperty(RESPONSE_BUFFER_PUBLISHER);
    }

    /**
     * Set the response {@link Publisher Publisher&lt;Buffer&gt;}.
     *
     * @param bufferPublisher the response content {@link Publisher Publisher&lt;Buffer&gt;}
     * @param reqCtx the {@link ContainerRequestContext} for the request
     */
    public static void setResponseBufferPublisher(final Publisher<Buffer> bufferPublisher,
                                                  final ContainerRequestContext reqCtx) {
        reqCtx.setProperty(RESPONSE_BUFFER_PUBLISHER, requireNonNull(bufferPublisher));
    }

    /**
     * Get the response {@link HttpExecutionStrategy} used for offloading.
     *
     * @param reqCtx the {@link ContainerRequestContext} for the request
     * @return the response {@link HttpExecutionStrategy}
     */
    @Nullable
    public static HttpExecutionStrategy getResponseExecutionStrategy(final ContainerRequestContext reqCtx) {
        return (HttpExecutionStrategy) reqCtx.getProperty(RESPONSE_EXEC_STRATEGY);
    }

    /**
     * Set the response {@link HttpExecutionStrategy} used for offloading.
     *
     * @param executor the response {@link HttpExecutionStrategy}
     * @param reqCtx the {@link ContainerRequestContext} for the request
     */
    public static void setResponseExecutionStrategy(final HttpExecutionStrategy executor,
                                                    final ContainerRequestContext reqCtx) {
        reqCtx.setProperty(RESPONSE_EXEC_STRATEGY, executor);
    }
}
