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
package io.servicetalk.examples.http.observer;

import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.http.utils.HttpLifecycleObservers;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * An example server that shows {@link HttpLifecycleObserver} usage.
 */
public final class LifecycleObserverServer {
    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .lifecycleObserver(HttpLifecycleObservers.logging("servicetalk-examples-http-observer-logger", TRACE))
                // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
                // streaming API see helloworld examples.
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .payloadBody("Hello LifecycleObserver!", textSerializerUtf8()))
                .awaitShutdown();
    }
}
