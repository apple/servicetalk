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
import org.junit.Ignore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.Priority;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import static io.servicetalk.http.router.jersey.TestUtil.asChunkPublisher;
import static javax.ws.rs.Priorities.ENTITY_CODER;

@Ignore("Publisher#toInputStream deadlock")
public class InterceptorsTest extends AbstractFilterInterceptorTest {
    @Priority(ENTITY_CODER)
    @Provider
    public static class TestInterceptor implements ReaderInterceptor, WriterInterceptor {
        @Context
        private ConnectionContext ctx;

        @Override
        public Object aroundReadFrom(final ReaderInterceptorContext readerInterceptorCtx) throws IOException {
            final InputStream old = readerInterceptorCtx.getInputStream();
            readerInterceptorCtx.setInputStream(new UpperCaseInputStream(old));
            try {
                return readerInterceptorCtx.proceed();
            } finally {
                readerInterceptorCtx.setInputStream(old);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void aroundWriteTo(final WriterInterceptorContext writerInterceptorCtx) throws IOException {
            // WriterInterceptor allows replacing the entity altogether so we can optimize
            // for cases when the resource has returned a Publisher
            if (writerInterceptorCtx.getEntity() instanceof Publisher) {
                writerInterceptorCtx.setEntity(((Publisher<HttpPayloadChunk>) writerInterceptorCtx.getEntity())
                        .concatWith(asChunkPublisher("!", ctx.getBufferAllocator(), ctx.getExecutor())));
                writerInterceptorCtx.proceed();
                return;
            }

            final OutputStream old = writerInterceptorCtx.getOutputStream();
            final ExclamatoryOutputStream eos = new ExclamatoryOutputStream(old);
            writerInterceptorCtx.setOutputStream(eos);
            try {
                writerInterceptorCtx.proceed();
            } finally {
                eos.finish();
                writerInterceptorCtx.setOutputStream(old);
            }
        }
    }

    public static class TestApplication extends ResourceConfig {
        public TestApplication() {
            super(
                    TestInterceptor.class,
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
