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

import io.servicetalk.http.api.StreamingHttpService;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static java.util.Collections.emptyIterator;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class HttpPredicateRouterBuilderHeaderTest extends BaseHttpPredicateRouterBuilderTest {

    @Test
    void testWhenHeaderIsPresent() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenHeader("host").isPresent().thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenHeaderFirstValue() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenHeader("host").firstValue("localhost").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost", "127.0.0.1"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1", "localhost"));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1"));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenHeaderFirstValueMatches() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenHeader("host").firstValueMatches("127\\..*").thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1", "localhost"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost", "127.0.0.1"));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost"));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenHeaderFirstValueMatchesPattern() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenHeader("host").firstValueMatches(Pattern.compile("127\\..*", CASE_INSENSITIVE))
                .thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1", "localhost"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost", "127.0.0.1"));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost"));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testWhenHeaderValues() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenHeader("host").values(new AnyMatchPredicate<>("localhost")).thenRouteTo(serviceA)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.valuesIterator("host")).then(answerIteratorOf("localhost"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1", "localhost"));
        assertSame(responseA, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).then(answerIteratorOf("127.0.0.1"));
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));

        when(headers.valuesIterator("host")).thenReturn(emptyIterator());
        assertSame(fallbackResponse, service.handle(ctx, request, reqRespFactory));
    }

    @Test
    void testMultipleHeaderRoutes() {
        final StreamingHttpService service = new HttpPredicateRouterBuilder()
                .whenHeader("host").firstValue("a.com").thenRouteTo(serviceA)
                .whenHeader("host").firstValue("b.com").thenRouteTo(serviceB)
                .whenHeader("host").firstValue("c.com").thenRouteTo(serviceC)
                .whenHeader("host").firstValue("d.com").thenRouteTo(serviceD)
                .when((ctx, req) -> true).thenRouteTo(fallbackService)
                .buildStreaming();

        when(headers.valuesIterator("host")).then(answerIteratorOf("d.com"));
        assertSame(responseD, service.handle(ctx, request, reqRespFactory));

        verify(request, times(4)).headers();
        verify(headers, times(4)).valuesIterator("host");
    }
}
