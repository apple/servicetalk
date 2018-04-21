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

import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext;

import org.glassfish.jersey.server.ApplicationHandler;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.ws.rs.core.Application;

import static org.mockito.Mockito.when;

public abstract class AbstractRequestHandlerTest {
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Mock
    protected ConnectionContext ctx;

    protected DefaultRequestHandler handler;

    @Before
    public void init() {
        when(ctx.getAllocator()).thenReturn(BufferAllocators.DEFAULT.getAllocator());
        handler = new DefaultRequestHandler(new ApplicationHandler(getApplication()));
    }

    protected abstract Application getApplication();
}
