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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.util.collection.Ref;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;

import static io.servicetalk.http.router.jersey.BufferPublisherInputStream.handleEntityStream;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.setResponseBufferPublisher;
import static javax.ws.rs.Priorities.ENTITY_CODER;
import static javax.ws.rs.core.MediaType.WILDCARD;

@Priority(ENTITY_CODER)
@Consumes(WILDCARD)
@Produces(WILDCARD)
abstract class AbstractMessageBodyReaderWriter<Source, T, SourceOfT, WrappedSourceOfT extends SourceOfT>
        implements MessageBodyReader<SourceOfT>, MessageBodyWriter<SourceOfT> {

    private final Class<Source> sourceClass;
    private final Class<T> contentClass;

    // We can not use `@Context ConnectionContext` directly because we would not see the latest version
    // in case it has been rebound as part of offloading.
    @Context
    protected Provider<Ref<ConnectionContext>> ctxRefProvider;

    @Context
    protected Provider<ContainerRequestContext> requestCtxProvider;

    protected AbstractMessageBodyReaderWriter(final Class<Source> sourceClass, final Class<T> contentClass) {
        this.sourceClass = sourceClass;
        this.contentClass = contentClass;
    }

    @Override
    public final boolean isReadable(final Class<?> type,
                                    final Type genericType,
                                    final Annotation[] annotations,
                                    final MediaType mediaType) {
        return isSupported(genericType);
    }

    final SourceOfT readFrom(final InputStream entityStream,
                             final BiFunction<Publisher<Buffer>, BufferAllocator, SourceOfT> bodyFunction,
                             final Function<SourceOfT, WrappedSourceOfT> sourceFunction)
            throws WebApplicationException {

        // The original BufferPublisherInputStream has been replaced via a filter/interceptor so we need to build
        // a new RS source from the actual input stream
        final BufferAllocator allocator = ctxRefProvider.get().get().executionContext().bufferAllocator();
        return handleEntityStream(entityStream, allocator, bodyFunction,
                (is, a) -> bodyFunction
                        .andThen(sourceFunction)
                        .apply(Publisher.from(is).map(a::wrap), a));
    }

    @Override
    public final boolean isWriteable(final Class<?> type,
                                     final Type genericType,
                                     final Annotation[] annotations,
                                     final MediaType mediaType) {
        return isSupported(genericType);
    }

    final void writeTo(final Publisher<Buffer> publisher) throws WebApplicationException {
        // The response entity being a Publisher, we do not need to write it to the entity stream
        // but instead store it in request context to bypass the stream writing infrastructure.
        setResponseBufferPublisher(publisher, requestCtxProvider.get());
    }

    private boolean isSupported(final Type entityType) {
        return isSourceOfType(entityType, sourceClass, contentClass);
    }

    static boolean isSourceOfType(final Type type, final Class<?> sourceClass, final Class<?> contentClass) {
        if (!(type instanceof ParameterizedType)) {
            return false;
        }

        final ParameterizedType parameterizedType = (ParameterizedType) type;
        final Type typeArgument;
        final Type rawType = parameterizedType.getRawType();

        return rawType instanceof Class &&
                sourceClass.isAssignableFrom((Class<?>) rawType) &&
                (typeArgument = getSingleTypeArgumentOrNull(parameterizedType)) != null &&
                typeArgument instanceof Class &&
                contentClass.isAssignableFrom((Class<?>) typeArgument);
    }

    @Nullable
    private static Type getSingleTypeArgumentOrNull(final ParameterizedType parameterizedType) {
        final Type[] typeArguments = parameterizedType.getActualTypeArguments();
        return typeArguments.length != 1 ? null : typeArguments[0];
    }
}
