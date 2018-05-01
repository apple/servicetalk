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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.inject.ReferencingFactory;
import org.glassfish.jersey.internal.util.collection.Ref;

import java.lang.reflect.Type;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.GenericType;

import static org.glassfish.jersey.internal.util.collection.Refs.emptyRef;

final class Context {
    private static final String CONNECTION_CONTEXT = ConnectionContext.class.getName();
    private static final String EXECUTOR = Executor.class.getName();
    private static final String RESPONSE_CHUNK_PUBLISHER_REF = new GenericType<Ref<Publisher<HttpPayloadChunk>>>() {
        // NOOP
    }.getType().toString();

    static final GenericType<Ref<ConnectionContext>> CONNECTION_CONTEXT_REF_GENERIC_TYPE =
            new GenericType<Ref<ConnectionContext>>() {
            };

    static final Type CONNECTION_CONTEXT_REF_TYPE = CONNECTION_CONTEXT_REF_GENERIC_TYPE.getType();

    static final GenericType<Ref<HttpRequest<HttpPayloadChunk>>> HTTP_REQUEST_REF_GENERIC_TYPE =
            new GenericType<Ref<HttpRequest<HttpPayloadChunk>>>() {
            };

    static final Type HTTP_REQUEST_REF_TYPE = HTTP_REQUEST_REF_GENERIC_TYPE.getType();

    static final GenericType<HttpRequest<HttpPayloadChunk>> HTTP_REQUEST_GENERIC_TYPE =
            new GenericType<HttpRequest<HttpPayloadChunk>>() {
            };

    static final GenericType<Ref<Executor>> EXECUTOR_REF_GENERIC_TYPE =
            new GenericType<Ref<Executor>>() {
            };

    static final Type EXECUTOR_REF_TYPE = EXECUTOR_REF_GENERIC_TYPE.getType();

    static final class ConnectionContextReferencingFactory extends ReferencingFactory<ConnectionContext> {
        @Inject
        ConnectionContextReferencingFactory(final Provider<Ref<ConnectionContext>> referenceFactory) {
            super(referenceFactory);
        }
    }

    static final class HttpRequestReferencingFactory extends ReferencingFactory<HttpRequest<HttpPayloadChunk>> {
        @Inject
        HttpRequestReferencingFactory(final Provider<Ref<HttpRequest<HttpPayloadChunk>>> referenceFactory) {
            super(referenceFactory);
        }
    }

    static final class ExecutorReferencingFactory extends ReferencingFactory<Executor> {
        @Inject
        ExecutorReferencingFactory(final Provider<Ref<Executor>> referenceFactory) {
            super(referenceFactory);
        }
    }

    private Context() {
        // no instances
    }

    static Ref<Publisher<HttpPayloadChunk>> setContextProperties(final ContainerRequestContext requestContext,
                                                                 final ConnectionContext ctx,
                                                                 final Executor executor) {
        final Ref<Publisher<HttpPayloadChunk>> responseChunkPublisherRef = emptyRef();
        requestContext.setProperty(CONNECTION_CONTEXT, ctx);
        requestContext.setProperty(EXECUTOR, executor);
        requestContext.setProperty(RESPONSE_CHUNK_PUBLISHER_REF, responseChunkPublisherRef);
        return responseChunkPublisherRef;
    }

    static ConnectionContext getConnectionContext(final ContainerRequestContext requestContext) {
        return (ConnectionContext) requestContext.getProperty(CONNECTION_CONTEXT);
    }

    static Executor getExecutor(final ContainerRequestContext requestContext) {
        return (Executor) requestContext.getProperty(EXECUTOR);
    }

    @SuppressWarnings("unchecked")
    static Ref<Publisher<HttpPayloadChunk>> getResponseChunkPublisherRef(final ContainerRequestContext requestContext) {
        return (Ref<Publisher<HttpPayloadChunk>>) requestContext.getProperty(RESPONSE_CHUNK_PUBLISHER_REF);
    }
}
