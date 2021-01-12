/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopSecurityHandshakeObserver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.AsyncContextMap.Key.newKey;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_BEFORE_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ERROR_DURING_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_NO_CONTENT_AFTER_READ;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_THROW_ERROR;
import static java.lang.String.valueOf;
import static java.util.Collections.unmodifiableMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

@RunWith(Parameterized.class)
public class HttpTransportObserverAsyncContextTest extends AbstractNettyHttpServerTest {

    private static final AsyncContextMap.Key<String> CLIENT_KEY = newKey("client");
    private static final String CLIENT_VALUE = "client_value";

    private final AsyncContextCaptureTransportObserver clientObserver =
            new AsyncContextCaptureTransportObserver(CLIENT_KEY);
    private final HttpProtocol protocol;

    public HttpTransportObserverAsyncContextTest(HttpProtocol protocol) {
        super(CACHED, CACHED_SERVER);
        this.protocol = protocol;
        protocol(protocol.config);
        transportObserver(clientObserver, NoopTransportObserver.INSTANCE);
    }

    @Parameters(name = "protocol={0}")
    public static HttpProtocol[] data() {
        return HttpProtocol.values();
    }

    @Before
    public void setupContext() {
        AsyncContext.put(CLIENT_KEY, CLIENT_VALUE);
    }

    @Test
    public void echoRequestResponse() throws Exception {
        String requestContent = "request_content";
        testRequestResponse(newRequest(SVC_ECHO), OK, requestContent.length());
    }

    @Test
    public void clientErrorWrite() {
        StreamingHttpClient client = streamingHttpClient();
        assertThrows(ExecutionException.class, () -> client.request(client.post(SVC_NO_CONTENT_AFTER_READ)
                .payloadBody(Publisher.failed(DELIBERATE_EXCEPTION)))
                .toFuture().get());
        assertMap(clientObserver.storageMap(), CLIENT_VALUE);
    }

    @Test
    public void serverHandlerError() throws Exception {
        testRequestResponse(newRequest(SVC_THROW_ERROR), INTERNAL_SERVER_ERROR, 0);
    }

    @Test
    public void serverErrorBeforeRead() throws Exception {
        testServerReadError(SVC_ERROR_BEFORE_READ);
    }

    @Test
    public void serverErrorDuringRead() throws Exception {
        testServerReadError(SVC_ERROR_DURING_READ);
    }

    private StreamingHttpRequest newRequest(String path) {
        String requestContent = "request_content";
        return streamingHttpClient().post(path)
                .addHeader(CONTENT_LENGTH, valueOf(requestContent.length()))
                .payloadBody(getChunkPublisherFromStrings(requestContent));
    }

    private void testRequestResponse(StreamingHttpRequest request, HttpResponseStatus expectedStatus,
                                     int expectedResponseLength) throws Exception {
        StreamingHttpResponse response = streamingHttpClient().request(request).toFuture().get();
        assertResponse(response, protocol.version, expectedStatus, expectedResponseLength);
        assertMap(clientObserver.storageMap(), CLIENT_VALUE);
    }

    private void testServerReadError(String path) throws Exception {
        StreamingHttpResponse response = streamingHttpClient().request(newRequest(path))
                .toFuture().get();
        assertResponse(response, protocol.version, OK);
        assertThrows(ExecutionException.class, () -> response.payloadBody().ignoreElements().toFuture().get());
        assertMap(clientObserver.storageMap(), CLIENT_VALUE);
    }

    private static void assertMap(Map<String, String> map, String expected) {
        Set<Map.Entry<String, String>> entries = map.entrySet();
        assertThat("No entries in the map", entries, not(empty()));
        for (Map.Entry<String, String> entry : entries) {
            assertThat("Unexpected value for \"" + entry.getKey() + "\" callback",
                    entry.getValue(), equalTo(expected));
        }
    }

    private static final class AsyncContextCaptureTransportObserver implements TransportObserver {

        private final Map<String, String> storageMap = new ConcurrentSkipListMap<>();
        private final AsyncContextMap.Key<String> key;

        AsyncContextCaptureTransportObserver(AsyncContextMap.Key<String> key) {
            this.key = key;
        }

        Map<String, String> storageMap() {
            return unmodifiableMap(storageMap);
        }

        @Override
        public ConnectionObserver onNewConnection() {
            storageMap.put("onNewConnection", valueOf(AsyncContext.get(key)));
            return new AsyncContextCaptureConnectionObserver();
        }

        private class AsyncContextCaptureConnectionObserver implements ConnectionObserver {

            @Override
            public void onDataRead(final int size) {
                // AsyncContext is unknown at this point because this event is triggered by network
            }

            @Override
            public void onDataWrite(final int size) {
                // AsyncContext is unknown at this point because protocols can write multiple requests concurrently
            }

            @Override
            public void onFlush() {
                // AsyncContext is unknown at this point because protocols can flush multiple requests concurrently
            }

            @Override
            public SecurityHandshakeObserver onSecurityHandshake() {
                // AsyncContext is unknown at this point because this event is triggered by network
                return NoopSecurityHandshakeObserver.INSTANCE;
            }

            @Override
            public DataObserver connectionEstablished(final ConnectionInfo info) {
                // AsyncContext is unknown at this point because this event is triggered by network
                return new AsyncContextCaptureDataObserver();
            }

            @Override
            public MultiplexedObserver multiplexedConnectionEstablished(final ConnectionInfo info) {
                // AsyncContext is unknown at this point because this event is triggered by network
                return new AsyncContextCaptureMultiplexedObserver();
            }

            @Override
            public void connectionClosed(final Throwable error) {
                // AsyncContext is unknown at this point because this event is triggered by network
            }

            @Override
            public void connectionClosed() {
                // AsyncContext may be unknown at this point if the closure is initiated by the transport
            }
        }

        private class AsyncContextCaptureMultiplexedObserver implements MultiplexedObserver {

            @Override
            public StreamObserver onNewStream() {
                storageMap.put("onNewStream", valueOf(AsyncContext.get(key)));
                return new AsyncContextCaptureStreamObserver();
            }
        }

        private class AsyncContextCaptureStreamObserver implements StreamObserver {

            @Override
            public DataObserver streamEstablished() {
                storageMap.put("streamEstablished", valueOf(AsyncContext.get(key)));
                return new AsyncContextCaptureDataObserver();
            }

            @Override
            public void streamClosed(final Throwable error) {
                // AsyncContext may be unknown at this point if the closure is initiated by the transport
            }

            @Override
            public void streamClosed() {
                // AsyncContext may be unknown at this point if the closure is initiated by the transport
            }
        }

        private class AsyncContextCaptureDataObserver implements DataObserver {

            @Override
            public ReadObserver onNewRead() {
                storageMap.put("onNewRead", valueOf(AsyncContext.get(key)));
                return new AsyncContextCaptureReadObserver();
            }

            @Override
            public WriteObserver onNewWrite() {
                storageMap.put("onNewWrite", valueOf(AsyncContext.get(key)));
                return new AsyncContextCaptureWriteObserver();
            }
        }

        private class AsyncContextCaptureReadObserver implements ReadObserver {

            @Override
            public void requestedToRead(final long n) {
                storageMap.put("requestedToRead", valueOf(AsyncContext.get(key)));
            }

            @Override
            public void itemRead() {
                storageMap.put("itemRead", valueOf(AsyncContext.get(key)));
            }

            @Override
            public void readFailed(final Throwable cause) {
                storageMap.put("readFailed", valueOf(AsyncContext.get(key)));
            }

            @Override
            public void readComplete() {
                storageMap.put("readComplete", valueOf(AsyncContext.get(key)));
            }

            @Override
            public void readCancelled() {
                storageMap.put("readCancelled", valueOf(AsyncContext.get(key)));
            }
        }

        private class AsyncContextCaptureWriteObserver implements WriteObserver {

            @Override
            public void requestedToWrite(final long n) {
                storageMap.put("requestedToWrite", valueOf(AsyncContext.get(key)));
            }

            @Override
            public void itemReceived() {
                storageMap.put("itemReceived", valueOf(AsyncContext.get(key)));
            }

            @Override
            public void onFlushRequest() {
                storageMap.put("onFlushRequest", valueOf(AsyncContext.get(key)));
            }

            // For the following callbacks AsyncContext is unknown because protocols can write multiple requests
            // concurrently. Users should use other callbacks above to retrieve the request context and keep it in a
            // class local variable.
            @Override
            public void itemWritten() {
            }

            @Override
            public void writeFailed(final Throwable cause) {
            }

            @Override
            public void writeComplete() {
            }

            @Override
            public void writeCancelled() {
            }
        }
    }
}
