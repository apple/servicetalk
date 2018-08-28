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
package io.servicetalk.http.router.jersey.internal;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;

import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.GenericType;

import static java.util.Objects.requireNonNull;

/**
 * Helper methods used internally for accessing ServiceTalk-specific request properties.
 */
public final class RequestProperties {
    private static final String REQUEST_CHUNK_PUBLISHER_IS =
            new GenericType<ChunkPublisherInputStream>() { }.getType().getTypeName();

    private static final String RESPONSE_CHUNK_PUBLISHER =
            new GenericType<Publisher<HttpPayloadChunk>>() { }.getType().getTypeName();

    private RequestProperties() {
        // no instances
    }

    /**
     * Initialize all request properties.
     *
     * @param entityStream the {@link ChunkPublisherInputStream} associated with the request.
     * @param reqCtx the {@link ContainerRequestContext} for the request
     */
    public static void initRequestProperties(final ChunkPublisherInputStream entityStream,
                                             final ContainerRequestContext reqCtx) {
        reqCtx.setProperty(REQUEST_CHUNK_PUBLISHER_IS, requireNonNull(entityStream));
        reqCtx.setProperty(RESPONSE_CHUNK_PUBLISHER, null);
    }

    /**
     * Get the {@link ChunkPublisherInputStream} associated with the request.
     *
     * @param reqCtx the {@link ContainerRequestContext} for the request
     * @return the {@link ChunkPublisherInputStream} associated with the request
     */
    public static ChunkPublisherInputStream getRequestChunkPublisherInputStream(final ContainerRequestContext reqCtx) {
        return (ChunkPublisherInputStream) reqCtx.getProperty(REQUEST_CHUNK_PUBLISHER_IS);
    }

    /**
     * Get the response {@link Publisher Publisher&lt;HttpPayloadChunk&gt;}.
     *
     * @param reqCtx the {@link ContainerRequestContext} for the request
     * @return the response {@link Publisher Publisher&lt;HttpPayloadChunk&gt;} or {@code null} if none has been set.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Publisher<HttpPayloadChunk> getResponseChunkPublisher(final ContainerRequestContext reqCtx) {
        return (Publisher<HttpPayloadChunk>) reqCtx.getProperty(RESPONSE_CHUNK_PUBLISHER);
    }

    /**
     * Set the response {@link Publisher Publisher&lt;HttpPayloadChunk&gt;}.
     *
     * @param chunkPublisher the response content {@link Publisher Publisher&lt;HttpPayloadChunk&gt;}
     * @param reqCtx the {@link ContainerRequestContext} for the request
     */
    public static void setResponseChunkPublisher(final Publisher<HttpPayloadChunk> chunkPublisher,
                                                 final ContainerRequestContext reqCtx) {
        reqCtx.setProperty(RESPONSE_CHUNK_PUBLISHER, requireNonNull(chunkPublisher));
    }
}
