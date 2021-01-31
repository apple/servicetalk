/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.ContentCodingHttpRequesterFilter;
import io.servicetalk.http.api.ContentCodingHttpServiceFilter;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.internal.CharSequences.contentEquals;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.internal.HeaderUtils.encodingFor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.stream;
import static java.util.Collections.disjoint;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ServiceTalkContentCodingTest extends BaseContentCodingTest {

    private static final BiFunction<Scenario, List<Throwable>, StreamingHttpServiceFilterFactory> REQ_FILTER =
            (scenario, errors) -> new StreamingHttpServiceFilterFactory() {
        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override

                public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                            final StreamingHttpRequest request,
                                                            final StreamingHttpResponseFactory responseFactory) {
                    final ContentCodec reqEncoding = scenario.requestEncoding;
                    final List<ContentCodec> clientSupportedEncodings = scenario.clientSupported;

                    try {

                        String requestPayload = request.payloadBody(textDeserializer())
                                .collect(StringBuilder::new, StringBuilder::append)
                                .toFuture().get().toString();

                        assertEquals(payloadAsString((byte) 'a'), requestPayload);

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
                                            request.headers().get(ACCEPT_ENCODING, "NOT_PRESENT")));
                        }

                        if (!expectedReqAcceptedEncodings.isEmpty() && !actualReqAcceptedEncodings.isEmpty()) {
                            assertThat(actualReqAcceptedEncodings, equalTo(expectedReqAcceptedEncodings));
                        }

                        return super.handle(ctx, request, responseFactory);
                    } catch (Throwable t) {
                        errors.add(t);
                        return failed(t);
                    }
                }
            };
        }
    };

    static final BiFunction<Scenario, List<Throwable>, StreamingHttpClientFilterFactory> RESP_FILTER =
            (scenario, errors) -> new StreamingHttpClientFilterFactory() {
        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return super.request(delegate, strategy, request).map(response -> {
                        if (INTERNAL_SERVER_ERROR.equals(response.status())) {
                            // Ignore any further validations
                            return response;
                        }

                        List<ContentCodec> server = scenario.serverSupported;
                        List<ContentCodec> client = scenario.clientSupported;

                        ContentCodec expected = identity();
                        for (ContentCodec codec : client) {
                            if (server.contains(codec)) {
                                expected = codec;
                                break;
                            }
                        }

                        try {
                            assertEquals(expected, encodingFor(client, response.headers()
                                    .get(CONTENT_ENCODING, identity().name())));
                        } catch (Throwable t) {
                            errors.add(t);
                            throw t;
                        }
                        return response;
                    });
                }
            };
        }
    };

    private ServerContext serverContext;
    private BlockingHttpClient client;
    protected List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    public ServiceTalkContentCodingTest(final HttpProtocol protocol, final Codings serverCodings,
                                        final Codings clientCodings, final Compression compression,
                                        final boolean valid) {
        super(protocol, serverCodings, clientCodings, compression, valid);
    }

    @Before
    public void start() throws Exception {
        serverContext = newServiceTalkServer(scenario, errors);
        client = newServiceTalkClient(serverHostAndPort(serverContext), scenario, errors);
    }

    @After
    public void finish() throws Exception {
        client.close();
        serverContext.close();
    }

    protected BlockingHttpClient client() {
        return client;
    }

    @Override
    public void testCompatibility() throws Throwable {
        super.testCompatibility();
        verifyNoErrors();
    }

    private void verifyNoErrors() throws Throwable {
        if (!errors.isEmpty()) {
            throw errors.get(0);
        }
    }

    protected void assertSuccessful(final ContentCodec encoding) throws Throwable {
        assertResponse(client().request(client()
                .get("/")
                .encoding(encoding)
                .payloadBody(payloadAsString((byte) 'a'), textSerializer())).toStreamingResponse());

        final BlockingStreamingHttpClient blockingStreamingHttpClient = client().asBlockingStreamingClient();
        assertResponse(blockingStreamingHttpClient.request(blockingStreamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(singletonList(payloadAsString((byte) 'a')), textSerializer())).toStreamingResponse());

        final StreamingHttpClient streamingHttpClient = client().asStreamingClient();
        assertResponse(streamingHttpClient.request(streamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(from(payloadAsString((byte) 'a')), textSerializer())).toFuture().get());
    }

    private void assertResponse(final StreamingHttpResponse response) throws Throwable {
        verifyNoErrors();

        assertResponseHeaders(response.headers().get(CONTENT_ENCODING, "identity").toString());

        String responsePayload = response.payloadBody(textDeserializer()).collect(StringBuilder::new,
                StringBuilder::append).toFuture().get().toString();

        assertEquals(payloadAsString((byte) 'b'), responsePayload);
    }

    protected void assertNotSupported(final ContentCodec encoding) throws Exception {
        final BlockingStreamingHttpClient blockingStreamingHttpClient = client().asBlockingStreamingClient();
        final StreamingHttpClient streamingHttpClient = client().asStreamingClient();

        assertEquals(UNSUPPORTED_MEDIA_TYPE, client().request(client()
                .get("/")
                .encoding(encoding)
                .payloadBody(payloadAsString((byte) 'a'), textSerializer())).status());

        assertEquals(UNSUPPORTED_MEDIA_TYPE, blockingStreamingHttpClient.request(blockingStreamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(singletonList(payloadAsString((byte) 'a')), textSerializer())).status());

        assertEquals(UNSUPPORTED_MEDIA_TYPE, streamingHttpClient.request(streamingHttpClient
                .get("/")
                .encoding(encoding)
                .payloadBody(from(payloadAsString((byte) 'a')), textSerializer())).toFuture().get().status());
    }

    protected void assertResponseHeaders(final String contentEncodingValue) {
        final List<ContentCodec> clientSupportedEncodings = scenario.clientSupported;
        final List<ContentCodec> serverSupportedEncodings = scenario.serverSupported;

        if (disjoint(serverSupportedEncodings, clientSupportedEncodings)) {
            assertEquals(identity().name().toString(), contentEncodingValue);
        } else {
            assertNotNull("Response encoding not in the client supported list " +
                    "[" + clientSupportedEncodings + "]", encodingFor(clientSupportedEncodings, contentEncodingValue));

            assertNotNull("Response encoding not in the server supported list " +
                    "[" + serverSupportedEncodings + "]", encodingFor(serverSupportedEncodings, contentEncodingValue));
        }
    }

    static ServerContext newServiceTalkServer(final Scenario scenario, final List<Throwable> errors)
            throws Exception {
        HttpServerBuilder httpServerBuilder = HttpServers.forAddress(localAddress(0));

        StreamingHttpService service = (ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                .payloadBody(from(payloadAsString((byte) 'b')), textSerializer()));

        StreamingHttpServiceFilterFactory filterFactory = REQ_FILTER.apply(scenario, errors);

        return httpServerBuilder
                .protocols(scenario.protocol.config)
                .appendServiceFilter(new ContentCodingHttpServiceFilter(scenario.serverSupported,
                        scenario.serverSupported))
                .appendServiceFilter(filterFactory)
                .listenStreamingAndAwait(service);
    }

    static BlockingHttpClient newServiceTalkClient(final HostAndPort hostAndPort, final Scenario scenario,
                                                   final List<Throwable> errors) {
        return HttpClients
                .forSingleAddress(hostAndPort)
                .appendClientFilter(RESP_FILTER.apply(scenario, errors))
                .appendClientFilter(new ContentCodingHttpRequesterFilter(scenario.clientSupported))
                .protocols(scenario.protocol.config)
                .buildBlocking();
    }
}
