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
package io.servicetalk.examples.http.timeout;

import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;

import java.time.Duration;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;

/**
 * Extends the async 'Hello World!' example to demonstrate use of timeout filter.
 */
public final class TimeoutServer {
    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                // Filter enforces that responses must complete within 2 minutes or will be cancelled.
                .appendServiceFilter(new TimeoutHttpServiceFilter(Duration.ofMinutes(2)))
                .listenAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok()
                                .payloadBody("Hello World!", textSerializer())))
                .awaitShutdown();
    }
}
