/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.MaxRequestLimitExceededException;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ContextFilter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.IMMEDIATE;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class NettyHttpServerContextFilterTest extends AbstractNettyHttpServerTest {

    enum FilterMode {
        ACCEPT_ALL(true, (executor, context) -> success(true)),
        DELAY_ACCEPT_ALL(true, (executor, context) -> executor.timer(100, MILLISECONDS).concatWith(success(true))),
        REJECT_ALL(false, (executor, context) -> success(false)),
        DELAY_REJECT_ALL(false, (executor, context) ->
                executor.timer(100, MILLISECONDS).concatWith(success(false))),
        THROW_EXCEPTION(false, (executor, context) -> {
            throw DELIBERATE_EXCEPTION;
        }),
        DELAY_SINGLE_ERROR(false, (executor, context) ->
                executor.timer(100, MILLISECONDS).concatWith(error(DELIBERATE_EXCEPTION))),
        SINGLE_ERROR(false, (executor, context) -> error(new DeliberateException())),
        ACCEPT_ALL_CONSTANT(true, (executor, context) -> success(true)) {
            @Override
            ContextFilter getContextFilter(final Executor executor) {
                return ContextFilter.ACCEPT_ALL;
            }
        };

        private final boolean expectAccept;
        private final BiFunction<Executor, ConnectionContext, Single<Boolean>>
                contextFilterFunction;

        FilterMode(boolean expectAccept, BiFunction<Executor, ConnectionContext,
                Single<Boolean>> contextFilterFunction) {
            this.expectAccept = expectAccept;
            this.contextFilterFunction = contextFilterFunction;
        }

        ContextFilter getContextFilter(Executor executor) {
            return (context) -> contextFilterFunction.apply(executor, context);
        }
    }

    private final FilterMode filterMode;
    @Nullable
    private volatile SSLSession sslSession;

    public NettyHttpServerContextFilterTest(final boolean enableSsl, final ExecutorSupplier clientExecutorSupplier,
                                            final ExecutorSupplier serverExecutorSupplier,
                                            final FilterMode filterMode) {
        super(clientExecutorSupplier, serverExecutorSupplier);
        this.filterMode = filterMode;
        setSslEnabled(enableSsl);
        if (enableSsl) {
            setContextFilter(ctx -> {
                // Asserting that the SSL Session has been set by the time the filter is called must be done from the
                // test thread, in order to fail the test with a useful message.
                sslSession = ctx.sslSession();
                return filterMode.getContextFilter(serverExecutorSupplier.executorSupplier.get()).filter(ctx);
            });
        } else {
            setContextFilter(filterMode.getContextFilter(serverExecutorSupplier.executorSupplier.get()));
        }
    }

    @Parameterized.Parameters(name = "ssl={0} client={1} server={2} {3}")
    public static Iterable<Object[]> getContextFilters() {
        List<Object[]> parameters = new ArrayList<>();
        for (boolean ssl : Arrays.asList(false, true)) {
            for (ExecutorSupplier clientExecutorSupplier : Arrays.asList(IMMEDIATE, CACHED)) {
                for (ExecutorSupplier serverExecutorSupplier : Arrays.asList(IMMEDIATE, CACHED)) {
                    for (FilterMode filtermode : FilterMode.values()) {
                        parameters.add(new Object[]{
                                ssl, clientExecutorSupplier, serverExecutorSupplier, filtermode});
                    }
                }
            }
        }
        return parameters;
    }

    @Test
    public void testAcceptConnection() throws Exception {
        try {
            // Send a request, and wait for the response.
            // We do this to ensure that the server has had a chance to execute code if the connection was accepted.
            // This is necessary for the delayed tests to see the correct state of the acceptedConnection flag.
            final StreamingHttpRequest request = getStreamingRequestFactory().get(SVC_ECHO).payloadBody(
                    getChunkPublisherFromStrings("hello"));
            request.headers().set(CONTENT_LENGTH, "5");
            final StreamingHttpResponse response = makeRequest(request);
            assertResponse(response, HTTP_1_1, OK, singletonList("hello"));
            if (!filterMode.expectAccept) {
                throw new AssertionError("Expected filter to reject connection");
            }
        } catch (ExecutionException e) {
            // If we expect the connection to be rejected, then an exception here is ok.
            // We want to continue after the exception, to assert that the server did not accept the connection.
            if (filterMode.expectAccept) {
                throw new AssertionError("Unexpected exception while reading/writing request/response", e);
            }
            assertThat(e.getCause(), anyOf(instanceOf(IOException.class), instanceOf(MaxRequestLimitExceededException.class)));
        }

        if (getSslEnabled()) {
            assertNotNull("SslSession was not set by the time filter executed", sslSession);
        }
    }
}
