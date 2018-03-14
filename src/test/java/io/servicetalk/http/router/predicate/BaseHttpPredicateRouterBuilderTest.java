/**
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.util.Iterator;
import java.util.Spliterator;

import static io.servicetalk.http.router.predicate.Placeholders.HTTP_1_1;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.when;

public abstract class BaseHttpPredicateRouterBuilderTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final ExpectedException expected = ExpectedException.none();
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Mock
    HttpService<HttpPayloadChunk, HttpPayloadChunk> serviceA, serviceB, serviceC, serviceD, serviceE, fallbackService;
    @Mock
    ConnectionContext ctx;
    @Mock
    HttpRequest<HttpPayloadChunk> request;
    @Mock
    HttpHeaders headers;
    @Mock
    Single<HttpResponse<HttpPayloadChunk>> responseA, responseB, responseC, responseD, responseE, fallbackResponse;

    @Before
    public void setUp() {
        when(request.getVersion()).thenReturn(HTTP_1_1);
        when(request.getHeaders()).thenReturn(headers);

        when(serviceA.handle(ctx, request)).thenReturn(responseA);
        when(serviceB.handle(ctx, request)).thenReturn(responseB);
        when(serviceC.handle(ctx, request)).thenReturn(responseC);
        when(serviceD.handle(ctx, request)).thenReturn(responseD);
        when(serviceE.handle(ctx, request)).thenReturn(responseE);
        when(fallbackService.handle(ctx, request)).thenReturn(fallbackResponse);
    }

    @SuppressWarnings("unchecked")
    <T> Answer<Iterator<T>> answerIteratorOf(T... values) {
        return invocation -> asList(values).iterator();
    }

    @SuppressWarnings("unchecked")
    <T> Answer<Spliterator<T>> answerSpliteratorOf(T... values) {
        return invocation -> asList(values).spliterator();
    }
}
