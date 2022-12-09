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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceBase;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HttpServiceTest {

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("serviceProvider")
    void supportsHttpServiceVariantAtRuntime(HttpServiceBase service) throws Exception {
        assertNotNull(HttpServers.forAddress(localAddress(0)).listenServiceAndAwait(service));
    }

    @Test
    void failsOnUnknownService() {
        assertThrows(IllegalArgumentException.class, () -> {
            HttpServiceBase service = new HttpServiceBase() {
                @Override
                public HttpExecutionStrategy requiredOffloads() {
                    return HttpServiceBase.super.requiredOffloads();
                }
            };

            try (HttpServerContext ctx = HttpServers.forAddress(localAddress(0)).listenServiceAndAwait(service)) {
                ctx.closeGracefully();
            }
        });
    }

    static Stream<Arguments> serviceProvider() {
        return Stream.of(
                arguments(named("BlockingHttpService",
                        (BlockingHttpService) (ctx, request, responseFactory) -> responseFactory.ok())),
                arguments(named("BlockingStreamingHttpService",
                        (BlockingStreamingHttpService) (ctx, request, response) -> { })),
                arguments(named("HttpService",
                        (HttpService) (ctx, request, responseFactory) ->
                                Single.succeeded(responseFactory.ok()))),
                arguments(named("StreamingHttpService",
                        (StreamingHttpService) (ctx, request, responseFactory) ->
                                Single.succeeded(responseFactory.ok())))
                );
    }
}
