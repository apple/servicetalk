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
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.ext.Provider;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;

public class GlobalFiltersTest extends AbstractFilterInterceptorTest {
    @Provider
    public static class TestGlobalFilter implements ContainerRequestFilter, ContainerResponseFilter {
        @Context
        private ConnectionContext ctx;

        @Override
        public void filter(final ContainerRequestContext requestCtx) {
            requestCtx.setEntityStream(new UpperCaseInputStream(requestCtx.getEntityStream()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public void filter(final ContainerRequestContext requestCtx,
                           final ContainerResponseContext responseCtx) {

            // ContainerResponseFilter allows replacing the entity altogether so we can optimize
            // for cases when the resource has returned a Publisher, while making sure we correctly carry the
            // generic type of the entity so the correct response body writer will be used
            if (responseCtx.getEntity() instanceof Publisher) {
                final Publisher<HttpPayloadChunk> contentWithBang =
                        ((Publisher<HttpPayloadChunk>) responseCtx.getEntity())
                                .concatWith(success(newPayloadChunk(ctx.getBufferAllocator().fromAscii("!"))));
                responseCtx.setEntity(new GenericEntity<Publisher<HttpPayloadChunk>>(contentWithBang) {
                });
            } else {
                responseCtx.setEntityStream(new ExclamatoryOutputStream(responseCtx.getEntityStream()));
            }
        }
    }

    public static class TestApplication extends ResourceConfig {
        TestApplication() {
            super(
                    TestGlobalFilter.class,
                    SynchronousResources.class,
                    AsynchronousResources.class
            );
        }
    }

    @Override
    protected Application getApplication() {
        return new TestApplication();
    }
}
