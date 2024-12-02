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
        HttpLifecycleObserver observer = HttpLifecycleObservers.logging(
                "servicetalk-examples-http-observer-logger", TRACE);
        HttpServers.forPort(8080)
                // There are a few ways how to configure an observer depending on the desired scope of its visibility.
                // 1. Configuring it at the builder gives maximum visibility and captures entire request-response state,
                // including all filters and exception mappers.
                .lifecycleObserver(observer)
                // 2. Configuring it as a filter allows users to change the ordering of the observer compare to other
                // filters or make it conditional. This might be helpful in a few scenarios such as when the tracking
                // scope should be limited or when logging should include tracing/MDC context set by other preceding
                // filters. See javadoc of HttpLifecycleObserverServiceFilter for more details.
                // 2.a. At any position compare to other filters before offloading:
                // .appendNonOffloadingServiceFilter(new HttpLifecycleObserverServiceFilter(observer))
                // 2.b. At any position compare to other filters after offloading:
                // .appendServiceFilter(new HttpLifecycleObserverServiceFilter(observer))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .payloadBody("Hello LifecycleObserver!", textSerializerUtf8()))
                .awaitShutdown();
    }
}
