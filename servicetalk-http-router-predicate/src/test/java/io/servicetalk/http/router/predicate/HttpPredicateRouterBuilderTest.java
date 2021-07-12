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
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSession;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpPredicateRouterBuilderTest extends BaseHttpPredicateRouterBuilderTest {
    private final CompleteTestCompletable completableA = new CompleteTestCompletable();
    private final CompleteTestCompletable completableB = new CompleteTestCompletable();
    private final CompleteTestCompletable completableC = new CompleteTestCompletable();
    private final FailCompletable failCompletable = new FailCompletable();

    @Test
    void testFallback() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testDefaultFallback() throws Exception {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .buildStreaming();

        final Single<StreamingHttpResponse> responseSingle = service.handle(ctx, request, reqRespFactory);
        final StreamingHttpResponse response = responseSingle.toFuture().get();
        assert response != null;
        assertEquals(404, response.status().code());
    }

    @Test
    void testWhenMethod() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenMethod(POST).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.method()).thenReturn(POST);
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.method()).thenReturn(GET);
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenMethodIsOneOf() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenMethodIsOneOf(POST, PUT).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.method()).thenReturn(POST);
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.method()).thenReturn(PUT);
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.method()).thenReturn(GET);
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenPathEquals() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathEquals("/abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.path()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/abcd");
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenPathIsOneOf() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathIsOneOf("/abc", "/def").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.path()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/def");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/abcd");
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenPathStartsWith() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathStartsWith("/abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.path()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/abcdef");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/def");
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenPathMatches() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathMatches(".*abc").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.path()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/defabc");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/ABC");
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/abcdef");
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenPathMatchesPattern() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenPathMatches(Pattern.compile(".*ABC", CASE_INSENSITIVE)).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(request.path()).thenReturn("/abc");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/defabc");
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.path()).thenReturn("/abcdef");
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenIsSsl() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenIsSsl().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(ctx.sslSession()).thenReturn(mock(SSLSession.class));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(ctx.sslSession()).thenReturn(null);
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenIsNotSsl() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenIsNotSsl().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(ctx.sslSession()).thenReturn(null);
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(ctx.sslSession()).thenReturn(mock(SSLSession.class));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenPredicate() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .when(req -> HTTP_1_1.equals(req.version())).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(request.version()).thenReturn(HTTP_1_0);
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenBiPredicate() {
        final SocketAddress addr1 = mock(SocketAddress.class);
        final SocketAddress addr2 = mock(SocketAddress.class);
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .when((ctx, req) -> ctx.remoteAddress() == addr1).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(ctx.remoteAddress()).thenReturn(addr1);
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(ctx.remoteAddress()).thenReturn(addr2);
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testCloseAsyncClosesAllServices() throws Exception {
        when(serviceA.closeAsync()).thenReturn(completableA);
        when(serviceB.closeAsync()).thenReturn(completableB);
        when(serviceC.closeAsync()).thenReturn(completableC);

        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenMethod(GET).thenRouteTo(serviceA)
                .whenMethod(POST).thenRouteTo(serviceB)
                .when((ctx, req) -> true).thenRouteTo(serviceC)
                .buildStreaming();

        service.closeAsync().toFuture().get();

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
    void testCloseAsyncClosesAllServicesWhenFirstOneIsError() throws Exception {
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
            completable.toFuture().get();
            Assertions.fail("Expected an exception from `await`");
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

    static class CompleteTestCompletable extends LegacyTestCompletable {
        @Override
        public void handleSubscribe(final Subscriber subscriber) {
            super.handleSubscribe(subscriber);
            subscriber.onComplete();
        }
    }

    static class FailCompletable extends LegacyTestCompletable {
        @Override
        public void handleSubscribe(final Subscriber subscriber) {
            super.handleSubscribe(subscriber);
            subscriber.onSubscribe(Cancellable.IGNORE_CANCEL);
            subscriber.onError(DELIBERATE_EXCEPTION);
        }
    }
}
