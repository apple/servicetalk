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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.server.ApplicationHandler;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Function;
import javax.ws.rs.core.Application;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static org.mockito.Mockito.when;

public abstract class AbstractJerseyHttpServiceTest {
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Mock
    protected ConnectionContext ctx;

    protected HttpService<HttpPayloadChunk, HttpPayloadChunk> service;

    // Test helper that invokes httpService and awaits the single/unwrap exceptions
    protected Function<HttpRequest<HttpPayloadChunk>, HttpResponse<HttpPayloadChunk>> handler;

    @Before
    public void init() {
        when(ctx.getBufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);

        service = new DefaultJerseyHttpService(new ApplicationHandler(getApplication()), newCachedThreadExecutor());

        handler = req -> {
            try {
                return awaitIndefinitely(service.handle(ctx, req));
            } catch (final Throwable t) {
                final Throwable c = t.getCause();
                if (c instanceof RuntimeException) {
                    throw (RuntimeException) c;
                }
                throw new RuntimeException(c != null ? c : t);
            }
        };
    }

    protected abstract Application getApplication();
}
