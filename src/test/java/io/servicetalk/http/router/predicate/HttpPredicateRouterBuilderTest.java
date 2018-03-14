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
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import org.junit.Ignore;
import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.Await.await;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.router.predicate.Placeholders.GET;
import static io.servicetalk.http.router.predicate.Placeholders.HTTP_1_0;
import static io.servicetalk.http.router.predicate.Placeholders.HTTP_1_1;
import static io.servicetalk.http.router.predicate.Placeholders.POST;
import static io.servicetalk.http.router.predicate.Placeholders.PUT;
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
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Ignore("This won't work until there's an implementation of HttpResponse to return.")
    @Test
    public void testDefaultFallback() throws Exception {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .build();

        final Single<HttpResponse<HttpPayloadChunk>> responseSingle = service.handle(ctx, request);
        final HttpResponse<HttpPayloadChunk> response = awaitIndefinitely(responseSingle);
        assert response != null;
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    public void testWhenMethod() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenMethod(POST).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(request.getMethod()).thenReturn(POST);
        assertSame(responseA, service.handle(ctx, request));

        when(request.getMethod()).thenReturn(GET);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenMethodIsOneOf() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenMethodIsOneOf(POST, PUT).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(request.getMethod()).thenReturn(POST);
        assertSame(responseA, service.handle(ctx, request));

        when(request.getMethod()).thenReturn(PUT);
        assertSame(responseA, service.handle(ctx, request));

        when(request.getMethod()).thenReturn(GET);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathEquals() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenPathEquals("/abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcd");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathIsOneOf() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenPathIsOneOf("/abc", "/def").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/def");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcd");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathStartsWith() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenPathStartsWith("/abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcdef");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/def");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPathMatches() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenPathMatches(".*abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

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
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenPathMatches(Pattern.compile(".*ABC", CASE_INSENSITIVE)).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(request.getPath()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/defabc");
        assertSame(responseA, service.handle(ctx, request));

        when(request.getPath()).thenReturn("/abcdef");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenIsSsl() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenIsSsl().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(ctx.getSslSession()).thenReturn(mock(SSLSession.class));
        assertSame(responseA, service.handle(ctx, request));

        when(ctx.getSslSession()).thenReturn(null);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenIsNotSsl() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenIsNotSsl().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(ctx.getSslSession()).thenReturn(null);
        assertSame(responseA, service.handle(ctx, request));

        when(ctx.getSslSession()).thenReturn(mock(SSLSession.class));
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenPredicate() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .when(req -> req.getVersion() == HTTP_1_1).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        assertSame(responseA, service.handle(ctx, request));

        when(request.getVersion()).thenReturn(HTTP_1_0);
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenBiPredicate() {
        SocketAddress addr1 = mock(SocketAddress.class);
        SocketAddress addr2 = mock(SocketAddress.class);
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .when((ctx, req) -> ctx.getRemoteAddress() == addr1).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

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

        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenMethod(GET).thenRouteTo(serviceA)
                .whenMethod(POST).thenRouteTo(serviceB)
                .when((ctx, req) -> true).thenRouteTo(serviceC)
                .build();

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

        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenMethod(GET).thenRouteTo(serviceA)
                .whenMethod(POST).thenRouteTo(serviceB)
                .when((ctx, req) -> true).thenRouteTo(serviceC)
                .build();

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
