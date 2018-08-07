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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

import org.glassfish.jersey.server.AsyncContext;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.internal.process.Endpoint;
import org.glassfish.jersey.server.internal.process.RequestProcessingContext;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import static java.lang.Integer.MAX_VALUE;
import static javax.ws.rs.core.Response.noContent;

/**
 * A combined {@link ContainerRequestFilter} / {@link WriterInterceptor} that allows
 * using {@link Single Single&lt;Response|MyPojo&gt;} or {@link Completable} in lieu
 * of {@link CompletionStage CompletionStage&lt;Response|MyPojo&gt;}
 * or {@link CompletionStage CompletionStage&lt;Void&gt;} respectively. This class:
 * <ul>
 * <li>Decorates the target {@link Endpoint} if its return type is a {@link Single} or {@link Completable},</li>
 * <li>Sets the generic {@link Type} of the response to type of the {@link Single} value in order for
 * 3rd party {@link MessageBodyWriter}s to work properly (they do not know about {@link Single}).
 * Note that this is only done for {@link Single}s of any type except {@link Buffer}, since we do have a specific
 * {@link MessageBodyWriter} for {@link Buffer}s.</li>
 * </ul>
 * <p>
 * The {@link Endpoint} decorator wires the {@link Single} events to Jersey's {@link AsyncContext} events.
 */
// We must run after all other filters have kicked in, so MAX_VALUE for the lowest priority. The reason for this is
// that if a filter runs after this one and calls abort(), no response filter will be executed since Jersey relies
// on Endpoint instances of type ResourceMethodInvoker to get the response filters.
@Priority(MAX_VALUE)
final class SingleRequestFilterWriterInterceptor implements ContainerRequestFilter, WriterInterceptor {
    @Override
    public void filter(final ContainerRequestContext requestCtx) {
        final UriRoutingContext urc = (UriRoutingContext) requestCtx.getUriInfo();
        final Method resourceMethod = urc.getResourceMethod();
        if (resourceMethod == null) {
            return;
        }

        final Class<?> returnType = resourceMethod.getReturnType();
        if (Single.class.isAssignableFrom(returnType)) {
            urc.setEndpoint(new SingleAwareEndpoint(urc));
        } else if (Completable.class.isAssignableFrom(returnType)) {
            urc.setEndpoint(new CompletableAwareEndpoint(urc));
        }
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorCtx) throws IOException {
        if (isSingleOfAnythingButBuffer(writerInterceptorCtx.getGenericType())) {
            final ParameterizedType parameterizedType = (ParameterizedType) writerInterceptorCtx.getGenericType();
            writerInterceptorCtx.setGenericType(parameterizedType.getActualTypeArguments()[0]);
        }

        writerInterceptorCtx.proceed();
    }

    private boolean isSingleOfAnythingButBuffer(@Nullable final Type entityType) {
        if (!(entityType instanceof ParameterizedType)) {
            return false;
        }
        final ParameterizedType parameterizedType = (ParameterizedType) entityType;
        if (!Single.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
            return false;
        }
        final Type[] typeArguments = parameterizedType.getActualTypeArguments();
        final Type singleType = typeArguments[0];
        return !(singleType instanceof Class) || !Buffer.class.isAssignableFrom((Class<?>) singleType);
    }

    private abstract static class AbstractSourceAwareEndpoint<T> implements Endpoint, ResourceInfo {
        protected final UriRoutingContext uriRoutingContext;
        protected final Endpoint originalEndpoint;
        private final Class<T> sourceType;

        protected AbstractSourceAwareEndpoint(final UriRoutingContext uriRoutingContext,
                                              final Class<T> sourceType) {
            this.uriRoutingContext = uriRoutingContext;
            this.originalEndpoint = uriRoutingContext.getEndpoint();
            this.sourceType = sourceType;
        }

        @Nullable
        @Override
        public Class<?> getResourceClass() {
            return originalEndpoint instanceof ResourceInfo ?
                    ((ResourceInfo) originalEndpoint).getResourceClass() : null;
        }

        @Nullable
        @Override
        public Method getResourceMethod() {
            return originalEndpoint instanceof ResourceInfo ?
                    ((ResourceInfo) originalEndpoint).getResourceMethod() : null;
        }

        @Nullable
        @Override
        public ContainerResponse apply(final RequestProcessingContext requestProcessingCtx) {
            final ContainerResponse containerResponse = originalEndpoint.apply(requestProcessingCtx);
            final Object responseEntity = containerResponse.getEntity();
            if (responseEntity == null || !(sourceType.isAssignableFrom(responseEntity.getClass()))) {
                return containerResponse;
            }

            final AsyncContext asyncContext = requestProcessingCtx.asyncContext();
            if (asyncContext.isSuspended()) {
                throw new IllegalStateException("JAX-RS suspended responses can't be used with " + sourceType);
            }
            if (!asyncContext.suspend()) {
                throw new IllegalStateException("Failed to suspend request processing");
            }

            try {
                handleSource(sourceType.cast(responseEntity), asyncContext);
            } catch (final Throwable t) {
                uriRoutingContext.setEndpoint(originalEndpoint);
                asyncContext.resume(new RuntimeException("Failed to handle: " + responseEntity +
                        " as source of type: " + sourceType, t));
            }

            // Return null on current thread since response will be delivered asynchronously
            return null;
        }

        protected abstract void handleSource(T source, AsyncContext asyncContext);
    }

    private static final class SingleAwareEndpoint extends AbstractSourceAwareEndpoint<Single> {
        protected SingleAwareEndpoint(final UriRoutingContext uriRoutingContext) {
            super(uriRoutingContext, Single.class);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleSource(final Single source, final AsyncContext asyncContext) {
            source.doBeforeFinally(() -> uriRoutingContext.setEndpoint(originalEndpoint))
                    .doAfterError(asyncContext::resume)
                    .doAfterCancel(asyncContext::cancel)
                    .subscribe(asyncContext::resume);
        }
    }

    private static final class CompletableAwareEndpoint extends AbstractSourceAwareEndpoint<Completable> {

        protected CompletableAwareEndpoint(final UriRoutingContext uriRoutingContext) {
            super(uriRoutingContext, Completable.class);
        }

        @Override
        protected void handleSource(final Completable source, final AsyncContext asyncContext) {
            source.doBeforeFinally(() -> uriRoutingContext.setEndpoint(originalEndpoint))
                    .doAfterError(asyncContext::resume)
                    .doAfterCancel(asyncContext::cancel)
                    .doAfterComplete(() -> asyncContext.resume(noContent().build()))
                    .subscribe();
        }
    }
}
