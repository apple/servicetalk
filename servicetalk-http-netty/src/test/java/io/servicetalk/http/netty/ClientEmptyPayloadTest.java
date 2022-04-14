/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaderValues;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class ClientEmptyPayloadTest {
    @ParameterizedTest
    @MethodSource("testArgs")
    void firstRequestEmptyPayloadSecondRequestSucceeds(HttpRequestMethod method1, EncodingType type1,
                                                       HttpRequestMethod method2, EncodingType type2) throws Exception {
        try (ServerContext ctx = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx1, request, responseFactory) ->
                        responseFactory.ok().payloadBody(request.payloadBody()));
             BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(ctx)).buildBlocking()) {
            HttpRequest req1 = client.newRequest(method1, "/");
            setEncoding(req1.headers(), type1, 0);
            HttpResponse resp1 = client.request(req1.payloadBody(
                    client.executionContext().bufferAllocator().newBuffer()));

            // Supply a non-empty payload if the method supports it.
            String payload2 = method2 == HEAD || method2 == CONNECT || method2 == TRACE ? "" : "hello world2";
            HttpRequest req2 = client.newRequest(method2, "/");
            setEncoding(req2.headers(), type2, payload2.length());
            HttpResponse resp2 = client.request(req2.payloadBody(
                    client.executionContext().bufferAllocator().fromAscii(payload2)));

            assertResponse(resp1, "");
            assertResponse(resp2, payload2);
        }
    }

    private static void assertResponse(HttpResponse resp, String payloadBody) {
        assertThat(resp.status(), equalTo(OK));
        assertThat(resp.payloadBody().toString(UTF_8), equalTo(payloadBody));
    }

    private static void setEncoding(HttpHeaders headers, EncodingType type, int payloadSize) {
        switch (type) {
            case CONTENT_LENGTH:
                headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(payloadSize));
                break;
            case TRANSFER_ENCODING:
                headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                break;
            default:
                break;
        }
    }

    private static Collection<Arguments> testArgs() {
        List<HttpRequestMethod> allMethods = asList(GET, HEAD, POST, PUT, DELETE, CONNECT, OPTIONS, TRACE, PATCH);
        List<Arguments> args = new ArrayList<>(EncodingType.values().length * EncodingType.values().length *
                allMethods.size() * allMethods.size());
        for (EncodingType t1 : EncodingType.values()) {
            for (EncodingType t2 : EncodingType.values()) {
                for (HttpRequestMethod m1 : allMethods) {
                    for (HttpRequestMethod m2 : allMethods) {
                        args.add(Arguments.of(m1, t1, m2, t2));
                    }
                }
            }
        }
        return args;
    }

    private enum EncodingType {
        CONTENT_LENGTH,
        TRANSFER_ENCODING,
        NONE
    }
}
