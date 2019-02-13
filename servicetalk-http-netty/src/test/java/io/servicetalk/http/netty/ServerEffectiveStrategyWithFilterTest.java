/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.BlockingHttpClient;

import org.junit.Test;

public class ServerEffectiveStrategyWithFilterTest extends AbstractServerEffectiveStrategyTest {

    public ServerEffectiveStrategyWithFilterTest() {
        super(true);
    }

    @Test
    public void blockingService() throws Exception {
        BlockingHttpClient client = setContext(builder.listenBlockingAndAwait((ctx, request, factory) ->
                serviceExecutor.submit(() -> factory.ok().payloadBody(request.payloadBody())).toFuture().get()));
        client.request(client.get("/").payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        verifyOffloadCount();
        assertOffload(ServerOffloadPoint.ServiceHandle);
        assertOffload(ServerOffloadPoint.RequestPayload);
        assertOffload(ServerOffloadPoint.Response);
    }

    @Test
    public void blockingStreamingService() throws Exception {
        BlockingHttpClient client = setContext(builder.listenBlockingStreamingAndAwait((ctx, request, factory) ->
                serviceExecutor.submit(() -> factory.ok().payloadBody(request.payloadBody())).toFuture().get()));
        client.request(client.get("/").payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        verifyOffloadCount();
        assertOffload(ServerOffloadPoint.ServiceHandle);
        assertOffload(ServerOffloadPoint.RequestPayload);
        assertOffload(ServerOffloadPoint.Response);
    }

    @Test
    public void service() throws Exception {
        BlockingHttpClient client = setContext(builder.listenAndAwait((ctx, request, factory) ->
                serviceExecutor.submit(() -> factory.ok().payloadBody(request.payloadBody()))));
        client.request(client.get("/").payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        verifyOffloadCount();
        assertOffload(ServerOffloadPoint.ServiceHandle);
        assertOffload(ServerOffloadPoint.RequestPayload);
        assertOffload(ServerOffloadPoint.Response);
    }

    @Test
    public void streamingService() throws Exception {
        BlockingHttpClient client = setContext(builder.listenStreamingAndAwait((ctx, request, factory) ->
                serviceExecutor.submit(() -> factory.ok().payloadBody(request.payloadBody()))));
        client.request(client.get("/").payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello")));
        verifyOffloadCount();
        assertOffload(ServerOffloadPoint.ServiceHandle);
        assertOffload(ServerOffloadPoint.RequestPayload);
        assertOffload(ServerOffloadPoint.Response);
    }
}
