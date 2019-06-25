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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.netty.internal.FlushStrategies;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.CI;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public class FlushStrategyForServerApiTest extends AbstractNettyHttpServerTest {

    private final boolean useAggregatedApi;
    private boolean explicitlySetFlushStrategy;

    public FlushStrategyForServerApiTest(final boolean useAggregatedApi) {
        super(CACHED, CACHED_SERVER);
        this.useAggregatedApi = useAggregatedApi;
    }

    @Parameterized.Parameters(name = "aggregated={0}")
    public static Collection<Boolean> clientExecutors() {
        return asList(true, false);
    }

    @Override
    protected void service(final StreamingHttpService service) {
        if (useAggregatedApi) {
            super.service((ctx, request, responseFactory) -> {
                if (explicitlySetFlushStrategy) {
                    ((NettyConnectionContext) ctx).updateFlushStrategy(
                            (prev, isOriginal) -> FlushStrategies.flushOnEach());
                }
                return responseFactory.ok().addHeader(TRANSFER_ENCODING, CHUNKED)
                        .toResponse().map(response -> response.toStreamingResponse().payloadBody(Publisher.never()));
            });
        } else {
            super.service((ctx, request, responseFactory) -> succeeded(
                    responseFactory.ok().payloadBody(Publisher.never())));
        }
    }

    @Test
    public void aggregatedApiShouldFlushOnEnd() throws Exception {
        assumeThat(useAggregatedApi, is(true));
        final StreamingHttpConnection connection = streamingHttpConnection();

        final Single<StreamingHttpResponse> responseSingle = connection.request(connection.newRequest(GET, "/"));

        try {
            responseSingle.toFuture().get(CI ? 900 : 100, MILLISECONDS);
            fail("Expected timeout");
        } catch (TimeoutException e) {
            // We've given the server some time to write and send the metadata, if it was going to.
        }
    }

    @Test
    public void aggregatedApiShouldNotOverrideExplicit() throws Exception {
        assumeThat(useAggregatedApi, is(true));
        explicitlySetFlushStrategy = true;
        final StreamingHttpConnection connection = streamingHttpConnection();

        final Single<StreamingHttpResponse> responseSingle = connection.request(connection.newRequest(GET, "/"));
        final StreamingHttpResponse response = responseSingle.toFuture().get();
        assertNotNull(response);
    }

    @Test
    public void streamingApiShouldFlushOnEach() throws Exception {
        assumeThat(useAggregatedApi, is(false));
        final StreamingHttpConnection connection = streamingHttpConnection();

        final Single<StreamingHttpResponse> responseSingle = connection.request(connection.newRequest(GET, "/"));
        final StreamingHttpResponse response = responseSingle.toFuture().get();
        assertNotNull(response);
    }
}
