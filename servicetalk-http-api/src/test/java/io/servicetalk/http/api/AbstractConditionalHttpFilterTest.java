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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public abstract class AbstractConditionalHttpFilterTest {
    protected static final String FILTERED_HEADER = "X-Filtered";

    protected static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HttpProtocolVersion.HTTP_1_1);

    @ClassRule
    public static final ExecutionContextRule TEST_CTX = cached();

    protected static final Predicate<StreamingHttpRequest> TEST_REQ_PREDICATE = req -> "/accept".equals(req.path());

    protected static final BiFunction<StreamingHttpRequest, StreamingHttpResponseFactory, Single<StreamingHttpResponse>>
            TEST_REQ_HANDLER = (req, resFactory) -> succeeded(resFactory.ok()
            .setHeader(FILTERED_HEADER, req.headers().get(FILTERED_HEADER, "false")));

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    protected static StreamingHttpRequest markFiltered(StreamingHttpRequest req) {
        return req.setHeader(FILTERED_HEADER, "true");
    }

    protected static Completable markClosed(AtomicBoolean closed, Completable completable) {
        return new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                closed.set(true);
                toSource(completable).subscribe(subscriber);
            }
        };
    }

    protected abstract Single<StreamingHttpResponse> sendTestRequest(StreamingHttpRequest req);

    protected abstract AsyncCloseable returnConditionallyFilteredResource(AtomicBoolean closed);

    protected static HttpExecutionContext testHttpExecutionContext() {
        return new DefaultHttpExecutionContext(TEST_CTX.bufferAllocator(), TEST_CTX.ioExecutor(), TEST_CTX.executor(),
                defaultStrategy());
    }

    @Test
    public void predicateAccepts() throws Exception {
        testFilter(true);
    }

    @Test
    public void predicateRejects() throws Exception {
        testFilter(false);
    }

    private void testFilter(boolean expectAccepted) throws Exception {
        final StreamingHttpRequest req = REQ_RES_FACTORY.get(expectAccepted ? "/accept" : "/reject");
        final StreamingHttpResponse res = sendTestRequest(req).toFuture().get();
        assertThat(res.headers().get(FILTERED_HEADER), is(Boolean.toString(expectAccepted)));
    }

    @Test
    public void closeAsyncImpactsBoth() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        returnConditionallyFilteredResource(closed).closeAsync().toFuture().get();
        assertThat(closed.get(), is(true));
    }

    @Test
    public void closeAsyncGracefullyImpactsBoth() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        returnConditionallyFilteredResource(closed).closeAsyncGracefully().toFuture().get();
        assertThat(closed.get(), is(true));
    }
}
