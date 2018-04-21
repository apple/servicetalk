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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;

import java.net.URI;
import java.security.Principal;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.ws.rs.core.SecurityContext;

import static io.servicetalk.http.router.jersey.CharSequenceUtil.ensureNoLeadingSlash;
import static io.servicetalk.http.router.jersey.Context.CHUNK_PUBLISHER_REF_TYPE;
import static io.servicetalk.http.router.jersey.Context.CONNECTION_CONTEXT_REF_TYPE;
import static io.servicetalk.http.router.jersey.Context.HTTP_REQUEST_REF_TYPE;
import static io.servicetalk.http.router.jersey.DummyHttpUtil.getBaseUri;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.glassfish.jersey.internal.util.collection.Refs.emptyRef;
import static org.glassfish.jersey.server.internal.ContainerUtils.encodeUnsafeCharacters;

final class DefaultRequestHandler implements BiFunction<ConnectionContext, HttpRequest<HttpPayloadChunk>,
        HttpResponse<HttpPayloadChunk>> {

    private static final SecurityContext UNAUTHENTICATED_SECURITY_CONTEXT = new SecurityContext() {
        @Nullable
        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(final String role) {
            return false;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Nullable
        @Override
        public String getAuthenticationScheme() {
            return null;
        }
    };

    private final ApplicationHandler applicationHandler;

    DefaultRequestHandler(final ApplicationHandler applicationHandler) {
        if (!applicationHandler.getConfiguration().isEnabled(ServiceTalkFeature.class)) {
            throw new IllegalStateException("The " + ServiceTalkFeature.class.getSimpleName()
                    + " needs to be enabled for this application.");
        }

        this.applicationHandler = applicationHandler;
    }

    @Override
    public HttpResponse<HttpPayloadChunk> apply(final ConnectionContext ctx, final HttpRequest<HttpPayloadChunk> req) {
        final CharSequence baseUri = getBaseUri(ctx, req);
        final CharSequence path = ensureNoLeadingSlash(req.getRawPath());

        // Jersey needs URI-unsafe query chars to be encoded
        @Nullable
        final String encodedQuery = req.getRawQuery().isEmpty() ? null : encodeUnsafeCharacters(req.getRawQuery());

        final StringBuilder requestUriBuilder =
                new StringBuilder(baseUri.length() + path.length() +
                        (encodedQuery != null ? 1 + encodedQuery.length() : 0))
                        .append(baseUri)
                        .append(path);

        if (encodedQuery != null) {
            requestUriBuilder.append('?').append(encodedQuery);
        }

        final ContainerRequest containerRequest = new ContainerRequest(
                URI.create(baseUri.toString()),
                URI.create(requestUriBuilder.toString()),
                req.getMethod().getName().toString(US_ASCII),
                UNAUTHENTICATED_SECURITY_CONTEXT,
                new MapPropertiesDelegate());

        req.getHeaders().forEach(h ->
                containerRequest.getHeaders().add(h.getKey().toString(), h.getValue().toString()));

        final DummyChunkPublisherInputStream entityStream = new DummyChunkPublisherInputStream(req.getMessageBody());
        containerRequest.setEntityStream(entityStream);
        final Ref<Publisher<HttpPayloadChunk>> chunkPublisherRef = emptyRef();
        final DefaultContainerResponseWriter responseWriter =
                new DefaultContainerResponseWriter(req, ctx.getAllocator(), chunkPublisherRef);
        containerRequest.setWriter(responseWriter);

        containerRequest.setRequestScopedInitializer(injectionManager -> {
            injectionManager.<Ref<ConnectionContext>>getInstance(CONNECTION_CONTEXT_REF_TYPE).set(ctx);
            injectionManager.<Ref<HttpRequest<HttpPayloadChunk>>>getInstance(HTTP_REQUEST_REF_TYPE).set(req);
            injectionManager.<Ref<Ref<Publisher<HttpPayloadChunk>>>>getInstance(CHUNK_PUBLISHER_REF_TYPE)
                    .set(chunkPublisherRef);
        });

        // Handle the request synchronously because we do it on a dedicated request thread
        applicationHandler.handle(containerRequest);

        return responseWriter.getResponse();
    }
}
