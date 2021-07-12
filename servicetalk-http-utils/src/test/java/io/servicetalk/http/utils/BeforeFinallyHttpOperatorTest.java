/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SingleOperator;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

@ExtendWith(MockitoExtension.class)
class BeforeFinallyHttpOperatorTest {
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @Mock
    private TerminalSignalConsumer beforeFinally;

    private SingleOperator<StreamingHttpResponse, StreamingHttpResponse> operator;

    @BeforeEach
    void setUp() {
        operator = new BeforeFinallyHttpOperator(beforeFinally);
    }

    @Test
    void nullAsSuccess() {
        final ResponseSubscriber subscriber = new ResponseSubscriber();

        toSource(Single.<StreamingHttpResponse>succeeded(null).liftSync(operator)).subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));
        verify(beforeFinally).onComplete();

        subscriber.verifyNullResponseReceived();
        verifyNoMoreInteractions(beforeFinally);
    }

    @Test
    void duplicateOnSuccess() {
        AtomicReference<SingleSource.Subscriber<? super StreamingHttpResponse>> subRef = new AtomicReference<>();

        Single<StreamingHttpResponse> original = new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                subRef.set(subscriber);
                subscriber.onSubscribe(IGNORE_CANCEL);
            }
        };

        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(original.liftSync(operator)).subscribe(subscriber);
        assertThat("Original Single not subscribed.", subRef.get(), is(notNullValue()));
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final StreamingHttpResponse response = reqRespFactory.newResponse(OK);
        subRef.get().onSuccess(response);
        final StreamingHttpResponse received = subscriber.verifyResponseReceived();
        verifyZeroInteractions(beforeFinally);

        final StreamingHttpResponse response2 = reqRespFactory.newResponse(OK);

        try {
            subRef.get().onSuccess(response2);
        } catch (AssertionError e) {
            // silence assert on dupe success when not canceled
        }

        final StreamingHttpResponse received2 = subscriber.verifyResponseReceived();
        // Old response should be preserved.
        assertThat("Duplicate response received.", received2, is(received));
        verifyZeroInteractions(beforeFinally);
    }

    @Test
    void cancelBeforeOnSuccess() {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle.liftSync(operator)).subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        subscriber.cancellable.cancel();
        verify(beforeFinally).cancel();
        responseSingle.verifyCancelled();

        final StreamingHttpResponse response = reqRespFactory.newResponse(OK);
        responseSingle.onSuccess(response);

        subscriber.verifyResponseReceived();
        verifyNoMoreInteractions(beforeFinally);
        assert subscriber.response != null;
        Exception ex = assertThrows(Exception.class, () -> subscriber.response.payloadBody().toFuture().get());
        assertThat(ex.getCause(), instanceOf(CancellationException.class));
    }

    @Test
    void cancelBeforeOnError() {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle.liftSync(operator)).subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        subscriber.cancellable.cancel();
        verify(beforeFinally).cancel();
        responseSingle.verifyCancelled();

        responseSingle.onError(DELIBERATE_EXCEPTION);
        assertThat("onError called post cancel.", subscriber.error, is(DELIBERATE_EXCEPTION));
        verifyNoMoreInteractions(beforeFinally);
    }

    @Test
    void cancelAfterOnSuccess() {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle.liftSync(operator)).subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final StreamingHttpResponse response = reqRespFactory.ok().payloadBody(never());
        responseSingle.onSuccess(response);

        verifyZeroInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();

        subscriber.cancellable.cancel();
        verifyZeroInteractions(beforeFinally);
        // We unconditionally cancel and let the original single handle the cancel post terminate
        responseSingle.verifyCancelled();
    }

    @Test
    void cancelAfterOnError() {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle.liftSync(operator)).subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onError(DELIBERATE_EXCEPTION);

        verify(beforeFinally).onError(DELIBERATE_EXCEPTION);
        assertThat("onError not called.", subscriber.error, is(DELIBERATE_EXCEPTION));

        subscriber.cancellable.cancel();
        verifyNoMoreInteractions(beforeFinally);
        // We unconditionally cancel and let the original single handle the cancel post terminate
        responseSingle.verifyCancelled();
    }

    @Test
    void payloadComplete() {
        TestPublisher<Buffer> payload = new TestPublisher<>();
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle.liftSync(operator)).subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final StreamingHttpResponse response = reqRespFactory.ok().payloadBody(payload);
        responseSingle.onSuccess(response);

        verifyZeroInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();
        assert subscriber.response != null;
        subscriber.response.payloadBody().forEach(chunk -> {
            //ignore
        });

        payload.onComplete();

        verify(beforeFinally).onComplete();
    }

    private static final class ResponseSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {

        Cancellable cancellable;
        @Nullable
        private StreamingHttpResponse response;
        private boolean responseReceived;
        Throwable error;

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse result) {
            responseReceived = true;
            this.response = result;
        }

        @Override
        public void onError(final Throwable t) {
            error = t;
        }

        void verifyNullResponseReceived() {
            assertThat("Response not received.", responseReceived, is(true));
            assertThat("Unexpected response.", response, is(nullValue()));
        }

        @Nullable
        StreamingHttpResponse verifyResponseReceived() {
            assertThat("Response not received.", responseReceived, is(true));
            assertThat("Unexpected response.", response, is(notNullValue()));
            return response;
        }
    }
}
