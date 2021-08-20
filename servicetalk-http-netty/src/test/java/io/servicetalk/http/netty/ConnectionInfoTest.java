/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;

class ConnectionInfoTest extends AbstractNettyHttpServerTest {

    @ParameterizedTest(name = "{index}: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void verifyToStringFormat(HttpProtocol protocol) throws Exception {
        CtxInterceptingServiceFilterFactory ff = new CtxInterceptingServiceFilterFactory();
        serviceFilterFactory(ff);
        protocol(protocol.config);
        setUp(CACHED, CACHED_SERVER);

        StreamingHttpConnection connection = streamingHttpConnection();
        assertFormat(connection.connectionContext().toString(), protocol, true);
        StreamingHttpResponse response = makeRequest(connection.get(SVC_ECHO));
        assertResponse(response, protocol.version, OK, 0);

        assertFormat(ff.queue.take(), protocol, false);
        assertThat(ff.queue, empty());
    }

    private static void assertFormat(String ctxStr, HttpProtocol protocol, boolean client) {
        assertThat(ctxStr, startsWith("[id: 0x"));
        assertThat(ctxStr, endsWith(client || protocol == HTTP_1 ? "]" : ")"));
    }

    private static final class CtxInterceptingServiceFilterFactory implements StreamingHttpServiceFilterFactory,
                                                                              HttpExecutionStrategyInfluencer {

        final BlockingQueue<String> queue = new LinkedBlockingDeque<>();

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(HttpServiceContext ctx,
                                                            StreamingHttpRequest request,
                                                            StreamingHttpResponseFactory responseFactory) {
                    queue.add(ctx.toString());
                    return delegate().handle(ctx, request, responseFactory);
                }
            };
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            return strategy;
        }
    }
}
