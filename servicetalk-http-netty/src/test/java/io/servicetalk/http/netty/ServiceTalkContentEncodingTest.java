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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.ContentEncodingHttpRequesterFilter;
import io.servicetalk.http.api.ContentEncodingHttpServiceFilter;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceTalkContentEncodingTest extends BaseContentEncodingTest {
    @Override
    protected void runTest(
            final HttpProtocol protocol, final Encoder clientEncoding, final Decoders clientDecoder,
            final Encoders serverEncoder, final Decoders serverDecoder, boolean valid) throws Throwable {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .appendServiceFilter(service -> new StreamingHttpServiceFilter(service) {
                    @Override
                    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                                final StreamingHttpRequest request,
                                                                final StreamingHttpResponseFactory responseFactory) {
                        return delegate().handle(ctx, request, responseFactory)
                                .map(resp -> {
                                    // content encoding should be stripped by the time the decoding is done.
                                    assertThat(resp.headers().get(ACCEPT_ENCODING), nullValue());

                                    CharSequence contentEncoding = resp.headers().get(CONTENT_ENCODING);
                                    boolean found = contentEncoding == null && (serverEncoder.list.isEmpty() ||
                                            !request.headers().contains(ACCEPT_ENCODING));
                                    for (BufferEncoder be : serverEncoder.list) {
                                        if (contentEncoding == null && contentEqualsIgnoreCase(be.encodingName(),
                                                identityEncoder().encodingName()) ||
                                                contentEncoding != null && contentEqualsIgnoreCase(be.encodingName(),
                                                        contentEncoding)) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    return found || !valid ? resp : responseFactory.ok().payloadBody(Publisher.from(
                                            "server error: invalid " + CONTENT_ENCODING + ": " + contentEncoding),
                                            appSerializerUtf8FixLen());
                                })
                                .onErrorReturn(AssertionError.class, cause ->
                                        responseFactory.ok().payloadBody(Publisher.from(
                                                "server error: " + cause.toString()), appSerializerUtf8FixLen()));
                    }
                })
                .appendServiceFilter(new ContentEncodingHttpServiceFilter(serverEncoder.list, serverDecoder.group))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    String requestPayload = request.payloadBody(textSerializerUtf8());
                    if (payloadAsString((byte) 'a').equals(requestPayload)) {
                        return responseFactory.ok().payloadBody(payloadAsString((byte) 'b'), textSerializerUtf8());
                    } else {
                        return responseFactory.badRequest().payloadBody(requestPayload, textSerializerUtf8());
                    }
                });
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                 .protocols(protocol.config)
                 .appendClientFilter(new ContentEncodingHttpRequesterFilter(clientDecoder.group))
                 .appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                     @Override
                     protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                     final HttpExecutionStrategy strategy,
                                                                     final StreamingHttpRequest request) {
                        return Single.defer(() -> {
                            assertHeader(() -> clientEncoding.encoder == null ? null :
                                            clientEncoding.encoder.encodingName(),
                                    request.headers().get(CONTENT_ENCODING), true);
                            assertHeader(clientDecoder.group::advertisedMessageEncoding,
                                    request.headers().get(ACCEPT_ENCODING), false);
                            return delegate.request(strategy, request).subscribeShareContext();
                        });
                     }
                 })
                .buildBlocking()) {
            HttpResponse response = client.request(client.get("/").contentEncoding(clientEncoding.encoder).payloadBody(
                    payloadAsString((byte) 'a'), textSerializerUtf8()));

            if (valid) {
                assertThat(response.status(), is(OK));

                // content encoding should be stripped by the time the decoding is done.
                assertThat(response.headers().get(CONTENT_ENCODING), nullValue());

                assertEquals(payloadAsString((byte) 'b'), response.payloadBody(textSerializerUtf8()));
            } else {
                assertThat(response.status(), is(UNSUPPORTED_MEDIA_TYPE));
            }
        }
    }

    private static void assertHeader(Supplier<CharSequence> expectedSupplier, @Nullable CharSequence actual,
                                     boolean allowNullForIdentity) {
        CharSequence expected = expectedSupplier.get();
        if (expected == null) {
            assertThat(actual, nullValue());
        } else if (actual != null || !allowNullForIdentity ||
                !contentEqualsIgnoreCase(identityEncoder().encodingName(), expected)) {
            assertThat("expected: '" + expected + "' actual: '" + actual + "'",
                    contentEqualsIgnoreCase(expected, actual), is(true));
        }
    }
}
