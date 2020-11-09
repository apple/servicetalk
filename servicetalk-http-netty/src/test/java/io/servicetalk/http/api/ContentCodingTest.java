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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.ContentCodings.deflateDefault;
import static io.servicetalk.http.api.ContentCodings.encodingFor;
import static io.servicetalk.http.api.ContentCodings.gzipDefault;
import static io.servicetalk.http.api.ContentCodings.identity;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.disjoint;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
                    final ContentCodec reqEncoding = options.requestEncoding;
                    final List<ContentCodec> clientSupportedEncodings = options.clientSupported;

                    try {

                        String requestPayload = request.payloadBody(textDeserializer())
                                .collect(StringBuilder::new, StringBuilder::append)
                                .toFuture().get().toString();

                        assertEquals(payload((byte) 'a'), requestPayload);

                        final List<String> actualReqAcceptedEncodings = stream(request.headers()
                                .get(ACCEPT_ENCODING, "NOT_PRESENT").toString().split(","))
                                .map((String::trim)).collect(toList());

                        final List<String> expectedReqAcceptedEncodings = clientSupportedEncodings.stream()
                                        .filter((enc) -> enc != identity())
                                        .map((ContentCodec::name))
                                        .map(CharSequence::toString)
                                        .collect(toList());

                        if (reqEncoding != identity()) {
                            assertTrue("Request encoding should be present in the request headers",
                                    contentEquals(reqEncoding.name(),
                                            request.headers().get(ACCEPT_ENCODING, "null")));
                        }

                        if (!expectedReqAcceptedEncodings.isEmpty() && !actualReqAcceptedEncodings.isEmpty()) {
                            assertThat(actualReqAcceptedEncodings, equalTo(expectedReqAcceptedEncodings));
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

    public ContentCodingTest(final List<ContentCodec> serverSupportedEncodings,
                             final List<ContentCodec> clientSupportedEncodings,
                             final ContentCodec requestEncoding, final boolean expectedSuccess,
                             final HttpProtocolConfig protocol) throws Exception {
        this.testEncodingScenario = new TestEncodingScenario(requestEncoding, clientSupportedEncodings,
                serverSupportedEncodings, protocol);
        this.expectedSuccess = expectedSuccess;

        httpServerBuilder = HttpServers.forAddress(localAddress(0))
                .enableWireLogging("server");
        serverContext = listenAndAwait();
        client = newClient();
    }

    @Parameterized.Parameters(name = "server-supported-encodings={0} client-supported-encodings={1} " +
            "request-encoding={2} expected-success={3} protocol={4}")
    public static Object[][] params() {
        return new Object[][] {
                {emptyList(), emptyList(), identity(), true, h1Default()},
                {emptyList(), emptyList(), identity(), true, h2Default()},
                {emptyList(), of(gzipDefault(), identity()), gzipDefault(), false, h1Default()},
                {emptyList(), of(gzipDefault(), identity()), gzipDefault(), false, h2Default()},
                {emptyList(), of(deflateDefault(), identity()), deflateDefault(), false, h1Default()},
                {emptyList(), of(deflateDefault(), identity()), deflateDefault(), false, h2Default()},
                {of(gzipDefault(), deflateDefault(), identity()), emptyList(), identity(), true, h1Default()},
                {of(gzipDefault(), deflateDefault(), identity()), emptyList(), identity(), true, h2Default()},
                {of(identity(), gzipDefault(), deflateDefault()),
                        of(gzipDefault(), identity()), gzipDefault(), true, h1Default()},
                {of(identity(), gzipDefault(), deflateDefault()),
                        of(gzipDefault(), identity()), gzipDefault(), true, h2Default()},
                {of(identity(), gzipDefault(), deflateDefault()),
                        of(deflateDefault(), identity()), deflateDefault(), true, h1Default()},
                {of(identity(), gzipDefault(), deflateDefault()),
                        of(deflateDefault(), identity()), deflateDefault(), true, h2Default()},
                {of(identity(), gzipDefault()), of(deflateDefault(), identity()), deflateDefault(), false, h1Default()},
                {of(identity(), gzipDefault()), of(deflateDefault(), identity()), deflateDefault(), false, h2Default()},
                {of(identity(), deflateDefault()), of(gzipDefault(), identity()), gzipDefault(), false, h1Default()},
                {of(identity(), deflateDefault()), of(gzipDefault(), identity()), gzipDefault(), false, h2Default()},
                {of(identity(), deflateDefault()),
                        of(deflateDefault(), identity()), deflateDefault(), true, h1Default()},
                {of(identity(), deflateDefault()),
                        of(deflateDefault(), identity()), deflateDefault(), true, h2Default()},
                {of(identity(), deflateDefault()), emptyList(), identity(), true, h1Default()},
                {of(identity(), deflateDefault()), emptyList(), identity(), true, h2Default()},
                {of(gzipDefault()), of(identity()), identity(), true, h1Default()},
                {of(gzipDefault()), of(identity()), identity(), true, h2Default()},
                {of(gzipDefault()), of(gzipDefault(), identity()), identity(), true, h1Default()},
                {of(gzipDefault()), of(gzipDefault(), identity()), identity(), true, h2Default()},
                {of(gzipDefault()), of(gzipDefault(), identity()), identity(), true, h1Default()},
                {of(gzipDefault()), of(gzipDefault(), identity()), identity(), true, h2Default()},
                {of(gzipDefault()), of(gzipDefault(), identity()), gzipDefault(), true, h1Default()},
                {of(gzipDefault()), of(gzipDefault(), identity()), gzipDefault(), true, h2Default()},
                {emptyList(), of(gzipDefault(), identity()), gzipDefault(), false, h1Default()},
                {emptyList(), of(gzipDefault(), identity()), gzipDefault(), false, h2Default()},
                {emptyList(), of(gzipDefault(), deflateDefault(), identity()), deflateDefault(), false, h1Default()},
                {emptyList(), of(gzipDefault(), deflateDefault(), identity()), deflateDefault(), false, h2Default()},
                {emptyList(), of(gzipDefault(), identity()), identity(), true, h1Default()},
                {emptyList(), of(gzipDefault(), identity()), identity(), true, h2Default()},
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
        StreamingHttpService service = (ctx, request, responseFactory) -> Single.succeeded(responseFactory.ok()
                .payloadBody(from(payload((byte) 'b')), textSerializer()));

        StreamingHttpServiceFilterFactory filterFactory = REQ_RESP_VERIFIER.apply(testEncodingScenario);

        return httpServerBuilder
                .protocols(testEncodingScenario.protocol)
                .appendServiceFilter(new ContentCodingHttpServiceFilter(testEncodingScenario.serverSupported))
                .appendServiceFilter(filterFactory)
                .listenStreamingAndAwait(service);
    }

    private HttpClient newClient() {
        return HttpClients
                .forSingleAddress(serverHostAndPort(serverContext))
                .appendClientFilter(new ContentCodingHttpClientFilter(testEncodingScenario.clientSupported))
                .protocols(testEncodingScenario.protocol)
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

    private void assertSuccessful(final ContentCodec encoding) throws Exception {
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
        final List<ContentCodec> clientSupportedEncodings = testEncodingScenario.clientSupported;
        final List<ContentCodec> serverSupportedEncodings = testEncodingScenario.serverSupported;

        final String respEncName = headers
                .get(CONTENT_ENCODING, "identity").toString();

        if (clientSupportedEncodings == null) {
            assertEquals(identity().name().toString(), respEncName);
        } else if (serverSupportedEncodings == null) {
            assertEquals(identity().name().toString(), respEncName);
        } else {
            if (disjoint(serverSupportedEncodings, clientSupportedEncodings)) {
                assertEquals(identity().name().toString(), respEncName);
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

    private void assertNotSupported(final ContentCodec encoding) throws Exception {
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

    private static List<ContentCodec> of(ContentCodec... encodings) {
        return asList(encodings);
    }

    static class TestEncodingScenario {
        final ContentCodec requestEncoding;
        final List<ContentCodec> clientSupported;
        final List<ContentCodec> serverSupported;
        final HttpProtocolConfig protocol;

        TestEncodingScenario(final ContentCodec requestEncoding,
                             final List<ContentCodec> clientSupported,
                             final List<ContentCodec> serverSupported,
                             final HttpProtocolConfig protocol) {
            this.requestEncoding = requestEncoding;
            this.clientSupported = clientSupported;
            this.serverSupported = serverSupported;
            this.protocol = protocol;
        }
    }
}
