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

import io.servicetalk.http.api.HttpCookie;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.Test;
import org.mockito.Mock;

import static java.util.Collections.emptyIterator;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

public class HttpPredicateRouterBuilderCookieTest extends BaseHttpPredicateRouterBuilderTest {

    @Mock
    HttpCookie cookie1, cookie2;

    @Test
    public void testWhenCookieIsPresent() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenCookie("session").isPresent().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.getCookies("session")).then(answerIteratorOf(cookie1));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    public void testWhenCookieIs() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenCookie("session").value(cookie1::equals).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.getCookies("session")).then(answerIteratorOf(cookie1));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).then(answerIteratorOf(cookie1, cookie2));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).then(answerIteratorOf(cookie2, cookie1));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).then(answerIteratorOf(cookie2));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    public void testWhenCookieValues() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenCookie("session").values(new AnyMatchPredicate<>(cookie1)).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.getCookies("session")).then(answerIteratorOf(cookie1));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).then(answerIteratorOf(cookie2, cookie1));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).then(answerIteratorOf(cookie2));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(headers.getCookies("session")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }
}
