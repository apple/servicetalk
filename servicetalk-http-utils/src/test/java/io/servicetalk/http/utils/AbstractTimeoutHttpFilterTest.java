/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.api.test.StepVerifiers;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TimeoutTracingInfoExtension.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static java.lang.Long.MAX_VALUE;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

abstract class AbstractTimeoutHttpFilterTest {

    abstract void newFilter(Duration duration);

    abstract Single<StreamingHttpResponse> applyFilter(Duration duration, boolean fullRequestResponse,
                                                       Single<StreamingHttpResponse> responseSingle);

    abstract Single<StreamingHttpResponse> applyFilter(TimeoutFromRequest timeoutForRequest,
                                                       boolean fullRequestResponse,
                                                       Single<StreamingHttpResponse> responseSingle);

    @Test
    void constructorValidatesDuration() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> newFilter(null));
        assertThrows(IllegalArgumentException.class, () -> newFilter(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> newFilter(ofNanos(1L).negated()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responseTimeout(boolean fullRequestResponse) {
        TestSingle<StreamingHttpResponse> responseSingle = new TestSingle<>();
        StepVerifiers.create(applyFilter(ofNanos(1L), fullRequestResponse, responseSingle))
                .expectError(TimeoutException.class)
                .verify();
        assertThat("No subscribe for response single", responseSingle.isSubscribed(), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responseWithZeroTimeout(boolean fullRequestResponse) {
        responseWithNonPositiveTimeout(ZERO, fullRequestResponse);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responseWithNegativeTimeout(boolean fullRequestResponse) {
        responseWithNonPositiveTimeout(ofNanos(1L).negated(), fullRequestResponse);
    }

    private void responseWithNonPositiveTimeout(Duration timeout, boolean fullRequestResponse) {
        TestSingle<StreamingHttpResponse> responseSingle = new TestSingle<>();
        StepVerifiers.create(applyFilter(__ -> timeout, fullRequestResponse, responseSingle))
                .expectError(TimeoutException.class)
                .verify();
        assertThat("No subscribe for payload body", responseSingle.isSubscribed(), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void responseCompletesBeforeTimeout(boolean fullRequestResponse) {
        TestSingle<StreamingHttpResponse> responseSingle = new TestSingle<>();
        StepVerifiers.create(applyFilter(ofSeconds(DEFAULT_TIMEOUT_SECONDS / 2), fullRequestResponse, responseSingle))
                .then(() -> immediate().schedule(() -> responseSingle.onSuccess(mock(StreamingHttpResponse.class)),
                        ofMillis(50L)))
                .expectSuccess()
                .verify();
        assertThat("No subscribe for response single", responseSingle.isSubscribed(), is(true));
    }

    @Test
    void payloadBodyTimeout() {
        TestPublisher<Buffer> payloadBody = new TestPublisher<>();
        AtomicBoolean responseSucceeded = new AtomicBoolean();
        StepVerifiers.create(applyFilter(ofMillis(100L), true, responseWith(payloadBody))
                .whenOnSuccess(__ -> responseSucceeded.set(true))
                .flatMapPublisher(StreamingHttpResponse::payloadBody))
                .thenRequest(MAX_VALUE)
                .expectError(TimeoutException.class)
                .verify();
        assertThat("Response did not succeeded", responseSucceeded.get(), is(true));
        assertThat("No subscribe for payload body", payloadBody.isSubscribed(), is(true));
    }

    @Test
    void payloadBodyDoesNotTimeoutWhenIgnored() {
        Duration timeout = ofMillis(100L);
        TestPublisher<Buffer> payloadBody = new TestPublisher<>();
        AtomicBoolean responseSucceeded = new AtomicBoolean();
        StepVerifiers.create(applyFilter(timeout, false, responseWith(payloadBody))
                .whenOnSuccess(__ -> responseSucceeded.set(true))
                .flatMapPublisher(StreamingHttpResponse::payloadBody))
                .expectSubscriptionConsumed(subscription ->
                        immediate().schedule(subscription::cancel, timeout.plusMillis(10L)))
                .thenRequest(MAX_VALUE)
                .expectNoSignals(timeout.plusMillis(5L))
                // FIXME: use thenCancel() instead of expectSubscriptionConsumed(...) + expectError()
                // https://github.com/apple/servicetalk/issues/1492
                .expectError(IllegalStateException.class)   // should never happen
                .verify();
        assertThat("Response did not succeeded", responseSucceeded.get(), is(true));
        assertThat("No subscribe for payload body", payloadBody.isSubscribed(), is(true));
    }

    @Test
    void subscribeToPayloadBodyAfterTimeout() {
        Duration timeout = ofMillis(100L);
        TestPublisher<Buffer> payloadBody = new TestPublisher<>();
        AtomicReference<StreamingHttpResponse> response = new AtomicReference<>();
        StepVerifiers.create(applyFilter(timeout, true, responseWith(payloadBody)))
                .expectSuccessConsumed(response::set)
                .verify();

        // Subscribe to payload body after timeout
        StepVerifiers.create(immediate().timer(timeout.plusMillis(5L)).concat(response.get().payloadBody()))
                .expectError(TimeoutException.class)
                .verify();
        assertThat("No subscribe for payload body", payloadBody.isSubscribed(), is(true));
    }

    @Test
    void payloadBodyCompletesBeforeTimeout() {
        TestPublisher<Buffer> payloadBody = new TestPublisher<>();
        AtomicReference<StreamingHttpResponse> response = new AtomicReference<>();
        StepVerifiers.create(applyFilter(ofSeconds(DEFAULT_TIMEOUT_SECONDS / 2), true, responseWith(payloadBody)))
                .expectSuccessConsumed(response::set)
                .verify();

        StepVerifiers.create(response.get().payloadBody())
                .then(() -> immediate().schedule(payloadBody::onComplete, ofMillis(50L)))
                .expectComplete()
                .verify();
        assertThat("No subscribe for payload body", payloadBody.isSubscribed(), is(true));
    }

    private static Single<StreamingHttpResponse> responseWith(Publisher<Buffer> payloadBody) {
        return succeeded(newResponse(OK, HTTP_1_1, EmptyHttpHeaders.INSTANCE, DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE).payloadBody(payloadBody));
    }
}
