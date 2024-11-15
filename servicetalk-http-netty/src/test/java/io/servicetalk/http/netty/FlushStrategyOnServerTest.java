/*
 * Copyright © 2019-2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class FlushStrategyOnServerTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String USE_AGGREGATED_RESP = "aggregated-resp";
    private static final String USE_EMPTY_RESP_BODY = "empty-resp-body";

    private final OutboundWriteEventsInterceptor interceptor = new OutboundWriteEventsInterceptor();
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private BlockingHttpClient client;

    private enum Param {
        NO_OFFLOAD(offloadNone()),
        DEFAULT(defaultStrategy()),
        OFFLOAD_ALL(customStrategyBuilder().offloadAll().build());
        private final HttpExecutionStrategy executionStrategy;
        Param(HttpExecutionStrategy executionStrategy) {
            this.executionStrategy = executionStrategy;
        }
    }

    private void setUp(final Param param) throws Exception {
        final StreamingHttpService service = (ctx, request, responseFactory) -> {
            StreamingHttpResponse resp = responseFactory.ok();
            if (request.headers().get(USE_EMPTY_RESP_BODY) == null) {
                resp.payloadBody(from("Hello", "World"), appSerializerUtf8FixLen());
            }
            if (request.headers().get(USE_AGGREGATED_RESP) != null) {
                return resp.toResponse().map(HttpResponse::toStreamingResponse);
            }
            return succeeded(resp);
        };

        serverContext = BuilderUtils.newServerBuilder(SERVER_CTX)
                .executionStrategy(param.executionStrategy)
                .transportObserver(interceptor)
                .listenStreamingAndAwait(service);
        client = newClientBuilder(serverContext, CLIENT_CTX).buildBlocking();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (serverContext != null) {
                serverContext.close();
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoAggregatedResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoAggregatedEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(true, true);
        assertFlushOnEnd();

        sendARequest(true, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void streamingResponsesFlushOnEach(final Param param) throws Exception {
        setUp(param);
        sendARequest(false);
        assertFlushOnEach();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoStreamingResponsesFlushOnEach(final Param param) throws Exception {
        setUp(param);
        sendARequest(false);
        assertFlushOnEach();

        sendARequest(false);
        assertFlushOnEach();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void streamingEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(false, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void twoStreamingEmptyResponsesFlushOnEnd(final Param param) throws Exception {
        setUp(param);
        sendARequest(false, true);
        assertFlushOnEnd();

        sendARequest(false, true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedAndThenStreamingResponse(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(false);
        assertFlushOnEach();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void streamingAndThenAggregatedResponse(final Param param) throws Exception {
        setUp(param);
        sendARequest(false);
        assertFlushOnEach();

        sendARequest(true);
        assertFlushOnEnd();
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy = {0}")
    @EnumSource(Param.class)
    void aggregatedStreamingEmptyResponse(final Param param) throws Exception {
        setUp(param);
        sendARequest(true);
        assertFlushOnEnd();

        sendARequest(false);
        assertFlushOnEach();

        sendARequest(false, true);
        assertFlushOnEnd();
    }

    private void assertFlushOnEnd() throws Exception {
        assertFlushOnEnd(interceptor);
    }

    static void assertFlushOnEnd(OutboundWriteEventsInterceptor interceptor) throws Exception {
        // aggregated response: headers, single (or empty) payload, and empty buffer instead of trailers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), greaterThan(0));
        assertThat("Unexpected writes", interceptor.pendingEvents(), is(0));
    }

    private void assertFlushOnEach() throws Exception {
        assertFlushOnEach(interceptor);
    }

    static void assertFlushOnEach(OutboundWriteEventsInterceptor interceptor) throws Exception {
        // headers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(1));
        // one chunk; chunk header payload and CRLF
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(3));
        // one chunk; chunk header payload and CRLF
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(3));
        // trailers
        assertThat("Unexpected writes", interceptor.takeWritesTillFlush(), is(1));
        assertThat("Unexpected writes", interceptor.pendingEvents(), is(0));
    }

    private void sendARequest(final boolean useAggregatedResp) throws Exception {
        sendARequest(useAggregatedResp, false);
    }

    private void sendARequest(boolean useAggregatedResp, boolean useEmptyRespBody) throws Exception {
        assert client != null;
        HttpRequest request = client.get("/")
                .setHeader(TRANSFER_ENCODING, CHUNKED)
                .payloadBody(client.executionContext().bufferAllocator().fromAscii("Hello"));
        if (useAggregatedResp) {
            request.setHeader(USE_AGGREGATED_RESP, "true");
        }
        if (useEmptyRespBody) {
            request.setHeader(USE_EMPTY_RESP_BODY, "true");
        }
        client.request(request);
    }

    static class OutboundWriteEventsInterceptor implements TransportObserver, ConnectionObserver {

        private static final Object MSG = new Object();
        private static final Object FLUSH = new Object();

        private final BlockingQueue<Object> writeEvents = new LinkedBlockingDeque<>();

        @Override
        public void onDataWrite(int size) {
            writeEvents.add(MSG);
        }

        @Override
        public void onFlush() {
            writeEvents.add(FLUSH);
        }

        int takeWritesTillFlush() throws Exception {
            int count = 0;
            for (;;) {
                Object evt = writeEvents.take();
                if (evt == FLUSH) {
                    return count;
                }

                count++;
            }
        }

        int pendingEvents() {
            return writeEvents.size();
        }

        @Override
        public ConnectionObserver onNewConnection(@Nullable Object localAddress, Object remoteAddress) {
            return this;
        }

        @Override
        public void onDataRead(int size) {
        }

        @Override
        public DataObserver connectionEstablished(final ConnectionInfo info) {
            return NoopTransportObserver.NoopDataObserver.INSTANCE;
        }

        @Override
        public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
            return NoopTransportObserver.NoopMultiplexedObserver.INSTANCE;
        }

        @Override
        public void connectionClosed(final Throwable error) {
        }

        @Override
        public void connectionClosed() {
        }
    }
}
