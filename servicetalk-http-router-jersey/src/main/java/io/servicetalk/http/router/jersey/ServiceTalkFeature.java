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

import io.servicetalk.http.router.jersey.Context.ConnectionContextReferencingFactory;
import io.servicetalk.http.router.jersey.Context.HttpRequestReferencingFactory;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import static io.servicetalk.http.router.jersey.Context.CONNECTION_CONTEXT_REF_GENERIC_TYPE;
import static io.servicetalk.http.router.jersey.Context.HTTP_REQUEST_GENERIC_TYPE;
import static io.servicetalk.http.router.jersey.Context.HTTP_REQUEST_REF_GENERIC_TYPE;
import static org.glassfish.jersey.internal.inject.ReferencingFactory.referenceFactory;

/**
 * Feature enabling ServiceTalk request handling.
 * This feature registers providers and binders needed to enable ServiceTalk as a handler for a Jersey application.
 */
public final class ServiceTalkFeature implements Feature {
    @Override
    public boolean configure(final FeatureContext context) {
        context.register(BufferMessageBodyWriter.class);
        context.register(BufferPublisherMessageBodyReaderWriter.class);
        context.register(BufferSingleMessageBodyReaderWriter.class);
        context.register(HttpPayloadChunkPublisherMessageBodyReaderWriter.class);
        context.register(SingleRequestFilterWriterInterceptor.class);

        context.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bindFactory(ConnectionContextReferencingFactory.class).to(ConnectionContext.class)
                        .proxy(true).proxyForSameScope(false).in(RequestScoped.class);
                bindFactory(referenceFactory()).to(CONNECTION_CONTEXT_REF_GENERIC_TYPE).in(RequestScoped.class);

                bindFactory(HttpRequestReferencingFactory.class).to(HTTP_REQUEST_GENERIC_TYPE)
                        .proxy(true).proxyForSameScope(false).in(RequestScoped.class);
                bindFactory(referenceFactory()).to(HTTP_REQUEST_REF_GENERIC_TYPE).in(RequestScoped.class);
            }
        });

        return true;
    }
}
