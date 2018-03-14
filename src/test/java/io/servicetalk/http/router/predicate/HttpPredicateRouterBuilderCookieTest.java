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
import io.servicetalk.http.api.HttpCookies;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.regex.Pattern;

import static java.util.Collections.emptyIterator;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

public class HttpPredicateRouterBuilderCookieTest extends BaseHttpPredicateRouterBuilderTest {

    @Mock
    HttpCookie cookie1, cookie2;

    @Mock
    HttpCookies cookies;

    @Before
    public void setUpCookies() {
        when(headers.parseCookies()).thenReturn(cookies);
        when(cookies.spliterator()).then(answerSpliteratorOf(cookie1, cookie2));
    }

    @Test
    public void testWhenCookieIsPresent() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenCookie("session").isPresent().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie1));
        assertSame(responseA, service.handle(ctx, request));

        when(cookies.getCookies("session")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenCookieIs() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenCookie("session").value(cookie1::equals).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie1));
        assertSame(responseA, service.handle(ctx, request));

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie1, cookie2));
        assertSame(responseA, service.handle(ctx, request));

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie2, cookie1));
        assertSame(responseA, service.handle(ctx, request));

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie2));
        assertSame(fallbackResponse, service.handle(ctx, request));

        when(cookies.getCookies("session")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenCookieValues() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenCookie("session").values(new AnyMatchPredicate<>(cookie1)).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie1));
        assertSame(responseA, service.handle(ctx, request));

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie2, cookie1));
        assertSame(responseA, service.handle(ctx, request));

        when(cookies.getCookies("session")).then(answerIteratorOf(cookie2));
        assertSame(fallbackResponse, service.handle(ctx, request));

        when(cookies.getCookies("session")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenCookieNameMatches() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenCookieNameMatches(".*abc.*").isPresent().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(cookie1.getName()).thenReturn("nope");

        when(cookie2.getName()).thenReturn("abcdef");
        assertSame(responseA, service.handle(ctx, request));

        when(cookie2.getName()).thenReturn("123abc");
        assertSame(responseA, service.handle(ctx, request));

        when(cookie2.getName()).thenReturn("stillnope");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }

    @Test
    public void testWhenCookieNameMatchesPattern() {
        final HttpService<HttpPayloadChunk, HttpPayloadChunk> service = new HttpPredicateRouterBuilder<HttpPayloadChunk, HttpPayloadChunk>()
                .whenCookieNameMatches(Pattern.compile(".*abc.*", Pattern.CASE_INSENSITIVE)).isPresent().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .build();

        when(cookie1.getName()).thenReturn("nope");

        when(cookie2.getName()).thenReturn("ABCDEF");
        assertSame(responseA, service.handle(ctx, request));

        when(cookie2.getName()).thenReturn("123ABC");
        assertSame(responseA, service.handle(ctx, request));

        when(cookie2.getName()).thenReturn("stillnope");
        assertSame(fallbackResponse, service.handle(ctx, request));
    }
}
