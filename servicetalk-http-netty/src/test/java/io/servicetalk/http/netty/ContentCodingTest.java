/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.ContentCoding;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.ContentCodings.deflate;
import static io.servicetalk.http.api.ContentCodings.encodingFor;
import static io.servicetalk.http.api.ContentCodings.gzip;
import static io.servicetalk.http.api.ContentCodings.none;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ContentCodingTest {

    private static final int PAYLOAD_SIZE = 512;
    private static final AtomicBoolean ASYNC_ERROR = new AtomicBoolean(false);

    private static final Function<TestEncodingScenario, StreamingHttpServiceFilterFactory> REQ_RESP_VERIFIER = (options)
            -> new StreamingHttpServiceFilterFactory() {
        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override

                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    final ContentCoding reqEncoding = options.requestEncoding;
                    final Set<ContentCoding> clientSupportedEncodings = options.clientSupported;

                    try {

                        String requestPayload = request.payloadBody(textDeserializer()).collect(StringBuilder::new,
                                StringBuilder::append).toFuture().get().toString();

                        assertEquals(payload((byte) 'a'), requestPayload);

                        final List<String> actualReqAcceptedEncodings = stream(request.headers()
                                .get(ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                                .map((String::trim)).collect(toList());

                        final List<String> expectedReqAcceptedEncodings = (clientSupportedEncodings == null) ?
                                emptyList() :
                                clientSupportedEncodings.stream()
                                        .filter((enc) -> enc != none())
                                        .map((ContentCoding::name))
                                        .collect(toList());

                        if (reqEncoding != none()) {
                            assertTrue("Request encoding should be present in the request headers",
                                    contentEquals(reqEncoding.name(),
                                            request.headers().get(ACCEPT_ENCODING, "null")));
                        }

                        if (!expectedReqAcceptedEncodings.isEmpty() && !actualReqAcceptedEncodings.isEmpty()) {
                            assertEquals(expectedReqAcceptedEncodings, actualReqAcceptedEncodings);
                        }
                    } catch (Throwable t) {
                        ASYNC_ERROR.set(true);
                        t.printStackTrace();
                        throw new RuntimeException(t);
                    }

                    return super.handle(ctx, request, responseFactory);
                }
            };
        }
    };

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final HttpServerBuilder httpServerBuilder;
    private final ServerContext serverContext;
    private final HttpClient client;
    protected final TestEncodingScenario testEncodingScenario;
    private final boolean expectedSuccess;

    public ContentCodingTest(final Set<ContentCoding> serverSupportedEncodings,
                             final Set<ContentCoding> clientSupportedEncodings,
                             final ContentCoding requestEncoding, final boolean expectedSuccess,
                             final Protocol protocol) throws Exception {
        this.testEncodingScenario = new TestEncodingScenario(requestEncoding, clientSupportedEncodings,
                serverSupportedEncodings, protocol);
        this.expectedSuccess = expectedSuccess;

        httpServerBuilder = HttpServers.forAddress(localAddress(0)).enableWireLogging("server");
        serverContext = listenAndAwait();
        client = newClient();
    }

    @Parameterized.Parameters(name = "server-supported-encodings={0} client-supported-encodings={1} " +
            "request-encoding={2} expected-success={3} protocol={4}")
    public static Object[][] params() {
        return new Object[][] {
                {null, null, none(), true, Protocol.H1},
                {null, null, none(), true, Protocol.H2},
                {null, of(gzip(), none()), gzip(), false, Protocol.H1},
                {null, of(gzip(), none()), gzip(), false, Protocol.H2},
                {null, of(deflate(), none()), deflate(), false, Protocol.H1},
                {null, of(deflate(), none()), deflate(), false, Protocol.H2},
                {of(gzip(), deflate(), none()), null, none(), true, Protocol.H1},
                {of(gzip(), deflate(), none()), null, none(), true, Protocol.H2},
                {of(none(), gzip(), deflate()), of(gzip(), none()), gzip(), true, Protocol.H1},
                {of(none(), gzip(), deflate()), of(gzip(), none()), gzip(), true, Protocol.H2},
                {of(none(), gzip(), deflate()), of(deflate(), none()), deflate(), true, Protocol.H1},
                {of(none(), gzip(), deflate()), of(deflate(), none()), deflate(), true, Protocol.H2},
                {of(none(), gzip()), of(deflate(), none()), deflate(), false, Protocol.H1},
                {of(none(), gzip()), of(deflate(), none()), deflate(), false, Protocol.H2},
                {of(none(), deflate()), of(gzip(), none()), gzip(), false, Protocol.H1},
                {of(none(), deflate()), of(gzip(), none()), gzip(), false, Protocol.H2},
                {of(none(), deflate()), of(deflate(), none()), deflate(), true, Protocol.H1},
                {of(none(), deflate()), of(deflate(), none()), deflate(), true, Protocol.H2},
                {of(none(), deflate()), null, none(), true, Protocol.H1},
                {of(none(), deflate()), null, none(), true, Protocol.H2},
                {of(gzip()), of(none()), none(), true, Protocol.H1},
                {of(gzip()), of(none()), none(), true, Protocol.H2},
                {of(gzip()), of(gzip(), none()), none(), true, Protocol.H1},
                {of(gzip()), of(gzip(), none()), none(), true, Protocol.H2},
                {of(gzip()), of(gzip(), none()), none(), true, Protocol.H1},
                {of(gzip()), of(gzip(), none()), none(), true, Protocol.H2},
                {of(gzip()), of(gzip(), none()), gzip(), true, Protocol.H1},
                {of(gzip()), of(gzip(), none()), gzip(), true, Protocol.H2},
                {null, of(gzip(), none()), gzip(), false, Protocol.H1},
                {null, of(gzip(), none()), gzip(), false, Protocol.H2},
                {null, of(gzip(), deflate(), none()), deflate(), false, Protocol.H1},
                {null, of(gzip(), deflate(), none()), deflate(), false, Protocol.H2},
                {null, of(gzip(), none()), none(), true, Protocol.H1},
                {null, of(gzip(), none()), none(), true, Protocol.H2},
        };
    }

    @After
    public void tearDown() throws Exception {
        ASYNC_ERROR.set(false);
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    private ServerContext listenAndAwait() throws Exception {
        HttpProtocolConfig config = testEncodingScenario.protocol.build(testEncodingScenario.serverSupported);

        StreamingHttpService service = (ctx, request, responseFactory) -> Single.succeeded(responseFactory.ok()
                .payloadBody(from(payload((byte) 'b')), textSerializer()));

        StreamingHttpServiceFilterFactory filterFactory = REQ_RESP_VERIFIER.apply(testEncodingScenario);
        return httpServerBuilder.appendServiceFilter(filterFactory)
                .protocols(config)
                .listenStreamingAndAwait(service);
    }

    private HttpClient newClient() {
        HttpProtocolConfig config = testEncodingScenario.protocol.build(testEncodingScenario.clientSupported);

        return HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .protocols(config)
                .build();
    }

    @Test
    public void test() throws Exception {
        if (expectedSuccess) {
            assertSuccessful(testEncodingScenario.requestEncoding);
        } else {
            assertNotSupported(testEncodingScenario.requestEncoding);
        }
    }

    private static String payload(byte b) {
        byte[] payload = new byte[PAYLOAD_SIZE];
        Arrays.fill(payload, b);
        return new String(payload, StandardCharsets.US_ASCII);
    }

    private void assertSuccessful(final ContentCoding encoding) throws Exception {
        assertResponse(client.request(client
                .get("/")
                .encoding(encoding)
                .payloadBody(payload((byte) 'a'), textSerializer())).toFuture().get().toStreamingResponse());

        final BlockingStreamingHttpClient blockingStreamingHttpClient = client.asBlockingStreamingClient();
        assertResponse(blockingStreamingHttpClient.request(blockingStreamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(asList(payload((byte) 'a')), textSerializer())).toStreamingResponse());

        final StreamingHttpClient streamingHttpClient = client.asStreamingClient();
        assertResponse(streamingHttpClient.request(streamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(from(payload((byte) 'a')), textSerializer())).toFuture().get());
        assertFalse(ASYNC_ERROR.get());
    }

    private void assertResponse(final StreamingHttpResponse response) {
        try {
            assertResponseHeaders(response.headers());

            String responsePayload = response.payloadBody(textDeserializer()).collect(StringBuilder::new,
                    StringBuilder::append).toFuture().get().toString();

            assertEquals(payload((byte) 'b'), responsePayload);
        } catch (Throwable t) {
            ASYNC_ERROR.set(true);
            t.printStackTrace();
        }
    }

    private void assertResponseHeaders(final HttpHeaders headers) {
        final Set<ContentCoding> clientSupportedEncodings = testEncodingScenario.clientSupported;
        final Set<ContentCoding> serverSupportedEncodings = testEncodingScenario.serverSupported;

        final List<String> actualRespAcceptedEncodings = stream(headers
                .get(ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                .map((String::trim)).collect(toList());

        final List<String> expectedRespAcceptedEncodings = (serverSupportedEncodings == null) ?
                emptyList() :
                serverSupportedEncodings.stream()
                        .filter((enc) -> enc != none())
                        .map((ContentCoding::name))
                        .collect(toList());

        if (!expectedRespAcceptedEncodings.isEmpty() && !actualRespAcceptedEncodings.isEmpty()) {
            assertEquals(expectedRespAcceptedEncodings, actualRespAcceptedEncodings);
        }

        final String respEncName = headers
                .get(CONTENT_ENCODING, "identity").toString();

        if (clientSupportedEncodings == null) {
            assertEquals(none().name(), respEncName);
        } else if (serverSupportedEncodings == null) {
            assertEquals(none().name(), respEncName);
        } else {
            if (disjoint(serverSupportedEncodings, clientSupportedEncodings)) {
                assertEquals(none().name(), respEncName);
            } else {
                assertNotNull("Response encoding not in the client supported list " +
                                "[" + clientSupportedEncodings + "]",
                        encodingFor(clientSupportedEncodings, valueOf(headers
                                .get(CONTENT_ENCODING, "identity"))));

                assertNotNull("Response encoding not in the server supported list " +
                                "[" + serverSupportedEncodings + "]",
                        encodingFor(serverSupportedEncodings, valueOf(headers
                                .get(CONTENT_ENCODING, "identity"))));
            }
        }
    }

    private void assertNotSupported(final ContentCoding encoding) throws Exception {
        final BlockingStreamingHttpClient blockingStreamingHttpClient = client.asBlockingStreamingClient();
        final StreamingHttpClient streamingHttpClient = client.asStreamingClient();

        assertEquals(UNSUPPORTED_MEDIA_TYPE, client.request(client
                .get("/")
                .encoding(encoding)
                .payloadBody(payload((byte) 'a'), textSerializer())).toFuture().get().status());

        assertEquals(UNSUPPORTED_MEDIA_TYPE, blockingStreamingHttpClient.request(blockingStreamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(asList(payload((byte) 'a')), textSerializer())).status());

        assertEquals(UNSUPPORTED_MEDIA_TYPE, streamingHttpClient.request(streamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(from(payload((byte) 'a')), textSerializer())).toFuture().get().status());
    }

    private static Set<ContentCoding> of(ContentCoding... encodings) {
        return new HashSet<>(asList(encodings));
    }

    static class TestEncodingScenario {
        final ContentCoding requestEncoding;
        @Nullable
        final Set<ContentCoding> clientSupported;
        @Nullable
        final Set<ContentCoding> serverSupported;
        final Protocol protocol;

        TestEncodingScenario(final ContentCoding requestEncoding,
                             final Set<ContentCoding> clientSupported,
                             final Set<ContentCoding> serverSupported,
                             final Protocol protocol) {
            this.requestEncoding = requestEncoding;
            this.clientSupported = clientSupported;
            this.serverSupported = serverSupported;
            this.protocol = protocol;
        }
    }

    enum Protocol {
        H1((supportedEncodings) -> {
            return HttpProtocolConfigs.h2()
                    .supportedEncodings(supportedEncodings == null ? emptySet() : supportedEncodings)
                    .build();
        }),
        H2((supportedEncodings) -> {
            return HttpProtocolConfigs.h1()
                    .supportedEncodings(supportedEncodings == null ? emptySet() : supportedEncodings)
                    .build();
        });

        private final Function<Set<ContentCoding>, HttpProtocolConfig> builder;
        Protocol(Function<Set<ContentCoding>, HttpProtocolConfig> builder) {
            this.builder = builder;
        }

        HttpProtocolConfig build(@Nullable final Set<ContentCoding> supportedEncodings) {
            return builder.apply(supportedEncodings);
        }
    }
}
