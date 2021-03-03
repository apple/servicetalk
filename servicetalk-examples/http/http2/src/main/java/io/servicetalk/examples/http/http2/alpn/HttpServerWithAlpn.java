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
package io.servicetalk.examples.http.http2.alpn;

import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;

/**
 * A server that negotiates <a href="https://tools.ietf.org/html/rfc7540#section-3.3">HTTP/2</a> or
 * <a href="https://tools.ietf.org/html/rfc7231">HTTP/1.1</a> using
 * <a href="https://tools.ietf.org/html/rfc7301">ALPN extension</a> for TLS connections.
 */
public final class HttpServerWithAlpn {

    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .protocols(h2Default(), h1Default()) // Configure support for HTTP/2 and HTTP/1.1 protocols
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .build())
                // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
                // streaming API see helloworld examples.
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody("I negotiate HTTP/2 or HTTP/1.1 via ALPN!", textSerializer()))
                .awaitShutdown();
    }
}
