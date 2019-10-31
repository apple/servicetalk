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
package io.servicetalk.examples.http.http2.priorknowledge.blocking;

import io.servicetalk.http.netty.HttpServers;

import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;

/**
 * Server with a blocking and aggregated programming paradigm that uses
 * <a href="https://tools.ietf.org/html/rfc7540#section-3.4">HTTP/2 with Prior Knowledge</a>.
 */
public final class BlockingHttp2Server {

    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .protocols(h2Default()) // Configure HTTP/2 Prior-Knowledge
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody("I speak HTTP/2!", textSerializer()))
                .awaitShutdown();
    }
}
