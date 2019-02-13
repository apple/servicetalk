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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Test;

public class ClientEffectiveStrategyTest extends AbstractClientEffectiveStrategyTest {

    public ClientEffectiveStrategyTest() throws Exception {
        super(false);
    }

    @Test
    public void blockingClient() throws Exception {
        BlockingHttpClient blockingClient = client.asBlockingClient();
        blockingClient.request(blockingClient.get("/"));
        verifyOffloadCount();
        assertNoOffload(ClientOffloadPoint.RequestPayloadSubscription);
        assertNoOffload(ClientOffloadPoint.ResponseMeta);
        assertNoOffload(ClientOffloadPoint.ResponseData);
    }

    @Test
    public void blockingStreamingClient() throws Exception {
        BlockingStreamingHttpClient blockingClient = client.asBlockingStreamingClient();
        BlockingIterator<Buffer> iter = blockingClient.request(blockingClient.get("/")).payloadBody().iterator();
        iter.forEachRemaining(__ -> { });
        iter.close();
        verifyOffloadCount();
        assertOffload(ClientOffloadPoint.RequestPayloadSubscription);
        assertNoOffload(ClientOffloadPoint.ResponseMeta);
        assertNoOffload(ClientOffloadPoint.ResponseData);
    }

    @Test
    public void streamingClient() throws Exception {
        client.request(client.get("/")).flatMapPublisher(StreamingHttpResponse::payloadBody).toFuture().get();
        verifyOffloadCount();
        assertOffload(ClientOffloadPoint.RequestPayloadSubscription);
        assertOffload(ClientOffloadPoint.ResponseMeta);
        assertOffload(ClientOffloadPoint.ResponseData);
    }

    @Test
    public void client() throws Exception {
        HttpClient httpClient = client.asClient();
        httpClient.request(httpClient.get("/")).toFuture().get();
        verifyOffloadCount();
        assertNoOffload(ClientOffloadPoint.RequestPayloadSubscription);
        assertOffload(ClientOffloadPoint.ResponseMeta);
        assertNoOffload(ClientOffloadPoint.ResponseData);
    }
}
