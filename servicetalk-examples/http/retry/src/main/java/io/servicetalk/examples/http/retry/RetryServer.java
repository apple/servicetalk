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
package io.servicetalk.examples.http.retry;

import io.servicetalk.http.netty.HttpServers;

import java.util.concurrent.atomic.AtomicLong;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * A "flaky" Hello World server that alternates between responding with "504" Gateway Timeout errors and "200" success.
 */
public final class RetryServer {
    public static void main(String[] args) throws Exception {
        AtomicLong responseCounter = new AtomicLong();
        HttpServers.forPort(8080)
                .listenAndAwait((ctx, request, responseFactory) ->
                            // half of all requests will fail with gateway timeout
                            succeeded((responseCounter.incrementAndGet() % 2L) == 0L ?
                                    responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()) :
                                    responseFactory.gatewayTimeout()))
                .awaitShutdown();
    }
}
