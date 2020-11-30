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
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.http.api.CharSequences.contentEquals;
import static io.servicetalk.http.api.HeaderUtils.encodingFor;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
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

    private static final Function<Scenario, StreamingHttpServiceFilterFactory> REQ_VERIFIER = (scenario)
            -> new StreamingHttpServiceFilterFactory() {
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
                        t.printStackTrace();
                        return succeeded(responseFactory.badRequest());
                    }
                }
            };
        }
    };

    static final Function<Scenario, StreamingHttpClientFilterFactory> RESP_VERIFIER = (scenario)
            -> new StreamingHttpClientFilterFactory() {
        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return super.request(delegate, strategy, request).map(response -> {
                        List<ContentCodec> server = scenario.serverSupported;
                        List<ContentCodec> client = scenario.clientSupported;

                        ContentCodec expected = identity();
                        for (ContentCodec codec : client) {
                            if (server.contains(codec)) {
                                expected = codec;
                                break;
                            }
                        }

                        assertEquals(expected, encodingFor(client, response.headers()
                                .get(CONTENT_ENCODING, "identity")));
                        return response;
                    });
                }
            };
        }
    };

    private ServerContext serverContext;
    private HttpClient client;

    public ServiceTalkContentCodingTest(Scenario scenario) {
        super(scenario);
    }

    @Before
    public void start() throws Exception {
        serverContext = newServiceTalkServer(scenario);
        client = newServiceTalkClient(serverHostAndPort(serverContext), scenario);
    }

    @After
    public void finish() throws Exception {
        client.close();
        serverContext.close();
    }

    protected HttpClient client() {
        return client;
    }

    protected void assertSuccessful(final ContentCodec encoding) throws Exception {
        assertResponse(client().request(client()
                .get("/")
                .encoding(encoding)
                .payloadBody(payloadAsString((byte) 'a'), textSerializer())).toFuture().get().toStreamingResponse());

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

    private void assertResponse(final StreamingHttpResponse response) throws Exception {
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
                .payloadBody(payloadAsString((byte) 'a'), textSerializer())).toFuture().get().status());

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

    static ServerContext newServiceTalkServer(final Scenario scenario) throws Exception {
        HttpServerBuilder httpServerBuilder = HttpServers.forAddress(localAddress(0));

        StreamingHttpService service = (ctx, request, responseFactory) -> Single.succeeded(responseFactory.ok()
                .payloadBody(from(payloadAsString((byte) 'b')), textSerializer()));

        StreamingHttpServiceFilterFactory filterFactory = REQ_VERIFIER.apply(scenario);

        return httpServerBuilder
                .protocols(scenario.protocol)
                .appendServiceFilter(new ContentCodingHttpServiceFilter(scenario.serverSupported,
                        scenario.serverSupported))
                .appendServiceFilter(filterFactory)
                .listenStreamingAndAwait(service);
    }

    static HttpClient newServiceTalkClient(final HostAndPort hostAndPort,
                                           final Scenario scenario) {
        return HttpClients
                .forSingleAddress(hostAndPort)
                .appendClientFilter(RESP_VERIFIER.apply(scenario))
                .appendClientFilter(new ContentCodingHttpRequesterFilter(scenario.clientSupported))
                .protocols(scenario.protocol)
                .build();
    }
}
