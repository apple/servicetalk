/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.debugging;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.netty.HttpServers;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * Extends the async "Hello World" sample with debugging features enabled.
 */
public final class DebuggingExampleServer {

    static {
        // Enables visibility for all wire log messages
        System.setProperty("servicetalk.logger.wireLogLevel", "TRACE");
        // Disables the context associated with individual request/responses to simplify execution tracing
        AsyncContext.disable();
    }

    public static void main(String... args) throws Exception {
        HttpServers.forPort(8080)
                // Disables asynchronous offloading to simplify execution tracing
                .executionStrategy(HttpExecutionStrategies.noOffloadsStrategy())
                // Enables detailed logging of I/O and I/O states.
                // Be sure to also enable the logger in your logging config file (log4j2.xml for this example)
                .enableWireLogging("servicetalk-test-wire-logger", TRACE, Boolean.TRUE::booleanValue)
                .listenAndAwait((ctx, request, responseFactory) -> {
                    String who = request.payloadBody(textDeserializer());
                    return succeeded(responseFactory.ok().payloadBody("Hello " + who + "!", textSerializer()));
                })
                .awaitShutdown();
    }
}
