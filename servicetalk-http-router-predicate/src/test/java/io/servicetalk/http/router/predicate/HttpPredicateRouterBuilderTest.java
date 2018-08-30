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
package io.servicetalk.http.router.predicate;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCompletable;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.Await.await;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpRequestMethods.PUT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpPredicateRouterBuilderTest extends BaseHttpPredicateRouterBuilderTest {

    final CompleteTestCompletable completableA = new CompleteTestCompletable();
    final CompleteTestCompletable completableB = new CompleteTestCompletable();
    final CompleteTestCompletable completableC = new CompleteTestCompletable();
    final FailCompletable failCompletable = new FailCompletable();

    @Test
    public void testFallback() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testDefaultFallback() throws Exception {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .buildStreaming();

        final Single<StreamingHttpResponse<HttpPayloadChunk>> responseSingle = service.handle(ctx, request);
        final StreamingHttpResponse<HttpPayloadChunk> response = awaitIndefinitely(responseSingle);
        assert response != null;
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    public void testWhenMethod() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenMethod(POST).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.getMethod()).thenReturn(POST);
        assertSame(responseA, service.handle(ctx, request));

        when(request.getMethod()).thenReturn(GET);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenMethodIsOneOf() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenMethodIsOneOf(POST, PUT).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.getMethod()).thenReturn(POST);
        assertSame(responseA, service.handle(ctx, request));

        when(request.getMethod()).thenReturn(PUT);
        assertSame(responseA, service.handle(ctx, request));

        when(request.getMethod()).thenReturn(GET);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathEquals() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathEquals("/abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcd");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathIsOneOf() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathIsOneOf("/abc", "/def").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/def");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcd");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathStartsWith() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathStartsWith("/abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcdef");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/def");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathMatches() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathMatches(".*abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/defabc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/ABC");
        assertSame(fallbackResponse, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcdef");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathMatchesPattern() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathMatches(Pattern.compile(".*ABC", CASE_INSENSITIVE)).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/defabc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcdef");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenIsSsl() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenIsSsl().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(ctx.getSslSession()).thenReturn(mock(SSLSession.class));
        assertSame(responseA, service.handle(ctx, request));

        when(ctx.getSslSession()).thenReturn(null);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenIsNotSsl() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenIsNotSsl().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(ctx.getSslSession()).thenReturn(null);
        assertSame(responseA, service.handle(ctx, request));

        when(ctx.getSslSession()).thenReturn(mock(SSLSession.class));
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPredicate() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .when(req -> req.getVersion() == HTTP_1_1).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        assertSame(responseA, service.handle(ctx, request));

        when(request.getVersion()).thenReturn(HTTP_1_0);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenBiPredicate() {
        final SocketAddress addr1 = mock(SocketAddress.class);
        final SocketAddress addr2 = mock(SocketAddress.class);
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .when((ctx, req) -> ctx.getRemoteAddress() == addr1).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(ctx.getRemoteAddress()).thenReturn(addr1);
        assertSame(responseA, service.handle(ctx, request));

        when(ctx.getRemoteAddress()).thenReturn(addr2);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testCloseAsyncClosesAllServices() throws Exception {
        when(serviceA.closeAsync()).thenReturn(completableA);
        when(serviceB.closeAsync()).thenReturn(completableB);
        when(serviceC.closeAsync()).thenReturn(completableC);

        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenMethod(GET).thenRouteTo(serviceA)
                .whenMethod(POST).thenRouteTo(serviceB)
                .when((ctx, req) -> true).thenRouteTo(serviceC)
                .buildStreaming();

        await(service.closeAsync(), 10, SECONDS);

        verify(serviceA).closeAsync();
        verify(serviceB).closeAsync();
        verify(serviceC).closeAsync();

        completableA.verifyNotCancelled();
        completableB.verifyNotCancelled();
        completableC.verifyNotCancelled();

        completableA.verifyListenCalled();
        completableB.verifyListenCalled();
        completableC.verifyListenCalled();
    }

    @Test
    public void testCloseAsyncClosesAllServicesWhenFirstOneIsError() throws Exception {
        when(serviceA.closeAsync()).thenReturn(failCompletable);
        when(serviceB.closeAsync()).thenReturn(completableB);
        when(serviceC.closeAsync()).thenReturn(completableC);

        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenMethod(GET).thenRouteTo(serviceA)
                .whenMethod(POST).thenRouteTo(serviceB)
                .when((ctx, req) -> true).thenRouteTo(serviceC)
                .buildStreaming();

        try {
            final Completable completable = service.closeAsync();
            await(completable, 10, SECONDS);
            fail("Expected an exception from `await`");
        } catch (final ExecutionException e) {
            assertSame(DELIBERATE_EXCEPTION, e.getCause());
        }

        verify(serviceA).closeAsync();
        verify(serviceB).closeAsync();
        verify(serviceC).closeAsync();

        failCompletable.verifyNotCancelled();
        completableB.verifyNotCancelled();
        completableC.verifyNotCancelled();

        failCompletable.verifyListenCalled();
        completableB.verifyListenCalled();
        completableC.verifyListenCalled();
    }

    public static class CompleteTestCompletable extends TestCompletable {
        @Override
        public void handleSubscribe(final Subscriber subscriber) {
            super.handleSubscribe(subscriber);
            subscriber.onComplete();
        }
    }

    public static class FailCompletable extends TestCompletable {
        @Override
        public void handleSubscribe(final Subscriber subscriber) {
            super.handleSubscribe(subscriber);
            subscriber.onSubscribe(Cancellable.IGNORE_CANCEL);
            subscriber.onError(DELIBERATE_EXCEPTION);
        }
    }
}
