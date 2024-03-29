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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpLifecycleObserverRequesterFilter;
import io.servicetalk.http.utils.HttpLifecycleObservers;

import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * An example singl-address client that shows {@link HttpLifecycleObserver} usage.
 */
public final class LifecycleObserverClient {
    public static void main(String[] args) throws Exception {
        // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
        // streaming API see helloworld examples.
        try (BlockingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                // Append this filter first for most cases to maximize visibility!
                // See javadocs on HttpLifecycleObserverRequesterFilter for more details on filter ordering.
                .appendClientFilter(new HttpLifecycleObserverRequesterFilter(
                        HttpLifecycleObservers.logging("servicetalk-examples-http-observer-logger", TRACE)))
                .buildBlocking()) {
            client.request(client.get("/"));
            // Ignore the response for this example. See logs for HttpLifecycleObserver results.
        }
    }
}
