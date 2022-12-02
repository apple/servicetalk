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
package io.servicetalk.examples.http.redirects;

import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static io.servicetalk.http.api.HttpSerializers.textSerializerAscii;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Starts two servers, one of them (HTTP) redirects to another (HTTPS).
 *
 * Run it first before executing any of the client-side examples in this package.
 */
public final class RedirectingServer {

    static final int SECURE_SERVER_PORT = 8443;
    static final int NON_SECURE_SERVER_PORT = 8080;

    static final CharSequence CUSTOM_HEADER = newAsciiString("custom-header");

    public static void main(String... args) throws Exception {
        ServerContext finalServer = HttpServers.forPort(SECURE_SERVER_PORT)
                // Note: DefaultTestCerts contains self-signed certificates that may be used only for local testing.
                // or demonstration purposes. Never use those for real use-cases.
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                // Wire-logging helps to demonstrate requests and responses that have been sent
                .enableWireLogging("servicetalk-examples-https-server-wire-logger", TRACE, Boolean.TRUE::booleanValue)
                .listenBlockingAndAwait(RedirectingServer::finalEndpoint);

        try {
            HttpServers.forPort(NON_SECURE_SERVER_PORT)
                    // Wire-logging helps to demonstrate requests and responses that have been sent
                    .enableWireLogging("servicetalk-examples-redirecting-server-wire-logger", TRACE,
                            Boolean.TRUE::booleanValue)
                    .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                        switch (request.requestTarget()) {
                            case "/relative":
                                return responseFactory.seeOther()
                                        .addHeader(LOCATION, "/final")
                                        .addHeader(SERVER, ctx.localAddress().toString());
                            case "/non-relative":
                                return responseFactory.movedPermanently()
                                        .addHeader(LOCATION, "https://localhost:" + SECURE_SERVER_PORT + "/final")
                                        .addHeader(SERVER, ctx.localAddress().toString());
                            case "/final":
                                return finalEndpoint(ctx, request, responseFactory);
                            default:
                                return responseFactory.notFound()
                                        .addHeader(SERVER, ctx.localAddress().toString());
                        }
                    })
                    .awaitShutdown();
        } finally {
            finalServer.closeAsync().toFuture().get();
        }
    }

    private static HttpResponse finalEndpoint(HttpServiceContext ctx, HttpRequest request,
                                              HttpResponseFactory responseFactory) {
        HttpResponse response = responseFactory.ok()
                .addHeader(SERVER, ctx.localAddress().toString());
        CharSequence customHeaderValue = request.headers().get(CUSTOM_HEADER);
        if (customHeaderValue != null) {
            response.addHeader(CUSTOM_HEADER, customHeaderValue);
        }
        return response.payloadBody("Redirect complete!" + (request.payloadBody().readableBytes() > 0 ?
                        " Request payloadBody received: " + request.payloadBody().toString(US_ASCII) :
                        " No request payload body received."),
                textSerializerAscii());
    }
}
