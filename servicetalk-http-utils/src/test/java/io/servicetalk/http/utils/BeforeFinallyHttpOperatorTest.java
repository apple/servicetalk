/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.lang.Long.MAX_VALUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class BeforeFinallyHttpOperatorTest {
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    @Mock
    private TerminalSignalConsumer beforeFinally;

    @SuppressWarnings("unused")
    private static Stream<Arguments> booleanTerminalNotification() {
        return Stream.of(Arguments.of(false, TerminalNotification.complete()),
                Arguments.of(false, TerminalNotification.error(DELIBERATE_EXCEPTION)),
                Arguments.of(true, TerminalNotification.complete()),
                Arguments.of(true, TerminalNotification.error(DELIBERATE_EXCEPTION)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void nullAsSuccess(boolean discardEventsAfterCancel) {
        final ResponseSubscriber subscriber = new ResponseSubscriber();

        toSource(Single.<StreamingHttpResponse>succeeded(null)
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));
        verify(beforeFinally).onComplete();

        subscriber.verifyNullResponseReceived();
        verifyNoMoreInteractions(beforeFinally);
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void duplicateOnSuccess(boolean discardEventsAfterCancel) {
        AtomicReference<SingleSource.Subscriber<? super StreamingHttpResponse>> subRef = new AtomicReference<>();

        Single<StreamingHttpResponse> original = new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                subRef.set(subscriber);
                subscriber.onSubscribe(IGNORE_CANCEL);
            }
        };

        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(original
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("Original Single not subscribed.", subRef.get(), is(notNullValue()));
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final StreamingHttpResponse response = reqRespFactory.newResponse(OK);
        subRef.get().onSuccess(response);
        final StreamingHttpResponse received = subscriber.verifyResponseReceived();
        verifyNoInteractions(beforeFinally);

        final StreamingHttpResponse response2 = reqRespFactory.newResponse(OK);

        try {
            subRef.get().onSuccess(response2);
        } catch (AssertionError e) {
            // silence assert on dupe success when not canceled
        }

        final StreamingHttpResponse received2 = subscriber.verifyResponseReceived();
        // Old response should be preserved.
        assertThat("Duplicate response received.", received2, is(received));
        verifyNoInteractions(beforeFinally);
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void cancelBeforeOnSuccess(boolean discardEventsAfterCancel) {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        subscriber.cancellable.cancel();
        verify(beforeFinally).cancel();
        responseSingle.verifyCancelled();

        responseSingle.onSuccess(reqRespFactory.newResponse(OK));
        if (discardEventsAfterCancel) {
            subscriber.verifyNoResponseReceived();
        } else {
            subscriber.verifyResponseReceived();
            assert subscriber.response != null;
            Exception ex = assertThrows(Exception.class, () -> subscriber.response.payloadBody().toFuture().get());
            assertThat(ex.getCause(), instanceOf(CancellationException.class));
        }
        verifyNoMoreInteractions(beforeFinally);
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void cancelBeforeOnSuccessNull(boolean discardEventsAfterCancel) {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        subscriber.cancellable.cancel();
        verify(beforeFinally).cancel();
        responseSingle.verifyCancelled();

        responseSingle.onSuccess(null);
        if (discardEventsAfterCancel) {
            subscriber.verifyNoResponseReceived();
        } else {
            subscriber.verifyNullResponseReceived();
        }
        verifyNoMoreInteractions(beforeFinally);
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void cancelBeforeOnError(boolean discardEventsAfterCancel) {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        subscriber.cancellable.cancel();
        verify(beforeFinally).cancel();
        responseSingle.verifyCancelled();

        responseSingle.onError(DELIBERATE_EXCEPTION);
        if (discardEventsAfterCancel) {
            assertThat("onError unexpectedly called post cancel.", subscriber.error, is(nullValue()));
        } else {
            assertThat("onError not called.", subscriber.error, is(DELIBERATE_EXCEPTION));
        }
        verifyNoMoreInteractions(beforeFinally);
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void cancelAfterOnSuccess(boolean discardEventsAfterCancel) {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onSuccess(reqRespFactory.ok().payloadBody(never()));

        verifyNoInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();

        subscriber.cancellable.cancel();
        verifyNoInteractions(beforeFinally);
        // We unconditionally cancel and let the original single handle the cancel post terminate
        responseSingle.verifyCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void cancelAfterOnError(boolean discardEventsAfterCancel) {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onError(DELIBERATE_EXCEPTION);

        verify(beforeFinally).onError(DELIBERATE_EXCEPTION);
        assertThat("onError not called.", subscriber.error, is(DELIBERATE_EXCEPTION));

        subscriber.cancellable.cancel();
        verifyNoMoreInteractions(beforeFinally);
        // We unconditionally cancel and let the original single handle the cancel post terminate
        responseSingle.verifyCancelled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0} payloadTerminal={1}")
    @MethodSource("booleanTerminalNotification")
    void cancelBeforeOnNextThenTerminate(boolean discardEventsAfterCancel, TerminalNotification payloadTerminal) {
        TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().disableAutoOnSubscribe().build();
        TestSubscription payloadSubscription = new TestSubscription();
        TestPublisherSubscriber<Buffer> payloadSubscriber = new TestPublisherSubscriber<>();

        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onSuccess(reqRespFactory.ok().payloadBody(payload));

        verifyNoInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();

        subscriber.cancellable.cancel();
        verifyNoInteractions(beforeFinally);
        // We unconditionally cancel and let the original single handle the cancel post terminate
        responseSingle.verifyCancelled();

        assert subscriber.response != null;
        toSource(subscriber.response.payloadBody()).subscribe(payloadSubscriber);
        payload.onSubscribe(payloadSubscription);
        payloadSubscriber.awaitSubscription().request(MAX_VALUE);

        assertThat("Payload was prematurely cancelled", payloadSubscription.isCancelled(), is(false));
        payloadSubscriber.awaitSubscription().cancel();
        assertThat("Payload was not cancelled", payloadSubscription.isCancelled(), is(true));

        payload.onNext(EMPTY_BUFFER);
        if (payloadTerminal.cause() == null) {
            payload.onComplete();
        } else {
            payload.onError(payloadTerminal.cause());
        }
        if (discardEventsAfterCancel) {
            assertThat("Unexpected payload body items", payloadSubscriber.pollAllOnNext(), empty());
            assertThat("Payload body terminated unexpectedly",
                    payloadSubscriber.pollTerminal(100, MILLISECONDS), is(nullValue()));
        } else {
            assertThat("Unexpected payload body items",
                    payloadSubscriber.pollAllOnNext(), contains(EMPTY_BUFFER));
            if (payloadTerminal.cause() == null) {
                payloadSubscriber.awaitOnComplete();
            } else {
                assertThat(payloadSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0} payloadTerminal={1}")
    @MethodSource("booleanTerminalNotification")
    void cancelAfterOnNextThenTerminate(boolean discardEventsAfterCancel, TerminalNotification payloadTerminal) {
        TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().disableAutoOnSubscribe().build();
        TestSubscription payloadSubscription = new TestSubscription();
        TestPublisherSubscriber<Buffer> payloadSubscriber = new TestPublisherSubscriber<>();

        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onSuccess(reqRespFactory.ok().payloadBody(payload));

        verifyNoInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();

        assert subscriber.response != null;
        toSource(subscriber.response.payloadBody()).subscribe(payloadSubscriber);
        payload.onSubscribe(payloadSubscription);
        payloadSubscriber.awaitSubscription().request(MAX_VALUE);

        payload.onNext(EMPTY_BUFFER);
        if (payloadTerminal.cause() == null) {
            payload.onComplete();
        } else {
            payload.onError(payloadTerminal.cause());
        }

        assertThat("Unexpected payload body items",
                payloadSubscriber.pollAllOnNext(), contains(EMPTY_BUFFER));
        if (payloadTerminal.cause() == null) {
            payloadSubscriber.awaitOnComplete();
        } else {
            assertThat(payloadSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        }

        assertThat("Payload was prematurely cancelled", payloadSubscription.isCancelled(), is(false));
        payloadSubscriber.awaitSubscription().cancel();
        assertThat("Payload was not cancelled", payloadSubscription.isCancelled(), is(true));
    }

    @Test
    void cancelWhileDeliveringPayload() {
        TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().disableAutoOnSubscribe().build();
        Subscription payloadSubscription = mock(Subscription.class);

        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, true)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onSuccess(reqRespFactory.ok().payloadBody(payload));

        verifyNoInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();

        assert subscriber.response != null;
        BlockingQueue<Buffer> receivedPayload = new LinkedBlockingDeque<>();
        AtomicReference<TerminalNotification> subscriberTerminal = new AtomicReference<>();
        toSource(subscriber.response.payloadBody()).subscribe(new PublisherSource.Subscriber<Buffer>() {

            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription subscription) {
                this.subscription = subscription;
                subscription.request(MAX_VALUE);
            }

            @Override
            public void onNext(@Nullable final Buffer buffer) {
                assert buffer != null;
                receivedPayload.add(buffer);
                if (receivedPayload.size() == 1) {
                    assert subscription != null;
                    subscription.cancel();
                    subscription.cancel();  // intentionally cancel two times to make sure it's idempotent
                    verify(payloadSubscription, Mockito.never()).cancel();
                    verifyNoMoreInteractions(beforeFinally);

                    payload.onNext(EMPTY_BUFFER);
                }
                verify(payloadSubscription, Mockito.never()).cancel();
                verifyNoMoreInteractions(beforeFinally);
                // Cancel will be propagated after this method returns
            }

            @Override
            public void onError(final Throwable t) {
                subscriberTerminal.set(TerminalNotification.error(t));
            }

            @Override
            public void onComplete() {
                subscriberTerminal.set(TerminalNotification.complete());
            }
        });
        payload.onSubscribe(payloadSubscription);

        verify(payloadSubscription, Mockito.never()).cancel();
        payload.onNext(EMPTY_BUFFER);
        verify(payloadSubscription).cancel();
        verify(beforeFinally).cancel();

        assertThat("Unexpected payload body items", receivedPayload, contains(EMPTY_BUFFER, EMPTY_BUFFER));
        assertThat("Unexpected payload body termination", subscriberTerminal.get(), is(nullValue()));

        verifyNoMoreInteractions(beforeFinally);
    }

    @ParameterizedTest(name = "{displayName} [{index}] fromOnNext={0} payloadTerminal={1}")
    @MethodSource("booleanTerminalNotification")
    void cancelWhileDeliveringPayloadThenTerminate(boolean fromOnNext, TerminalNotification payloadTerminal) {
        TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().disableAutoOnSubscribe().build();
        TestSubscription payloadSubscription = new TestSubscription();

        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, true)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onSuccess(reqRespFactory.ok().payloadBody(payload));

        verifyNoInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();

        assert subscriber.response != null;
        BlockingQueue<Buffer> receivedPayload = new LinkedBlockingDeque<>();
        AtomicReference<TerminalNotification> subscriberTerminal = new AtomicReference<>();
        toSource(subscriber.response.payloadBody()).subscribe(new PublisherSource.Subscriber<Buffer>() {

            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1L);
            }

            @Override
            public void onNext(@Nullable final Buffer buffer) {
                assert buffer != null;
                receivedPayload.add(buffer);
                if (fromOnNext) {
                    assert subscription != null;
                    subscription.cancel();
                }
                if (payloadTerminal.cause() == null) {
                    payload.onComplete();
                } else {
                    payload.onError(payloadTerminal.cause());
                }
            }

            @Override
            public void onError(final Throwable t) {
                subscriberTerminal.set(TerminalNotification.error(t));
                cancelFromTerminal();
            }

            @Override
            public void onComplete() {
                subscriberTerminal.set(TerminalNotification.complete());
                cancelFromTerminal();
            }

            private void cancelFromTerminal() {
                if (!fromOnNext) {
                    assert subscription != null;
                    subscription.cancel();
                }
            }
        });
        payload.onSubscribe(payloadSubscription);

        assertThat("Payload was prematurely cancelled", payloadSubscription.isCancelled(), is(false));
        payload.onNext(EMPTY_BUFFER);
        assertThat("Payload was not cancelled", payloadSubscription.isCancelled(), is(true));

        assertThat("Unexpected payload body items", receivedPayload, contains(EMPTY_BUFFER));
        assertThat("Unexpected payload body termination", subscriberTerminal.get(), equalTo(payloadTerminal));
        if (payloadTerminal.cause() == null) {
            verify(beforeFinally).onComplete();
        } else {
            verify(beforeFinally).onError(payloadTerminal.cause());
        }
        verifyNoMoreInteractions(beforeFinally);
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0}")
    @ValueSource(booleans = {false, true})
    void payloadComplete(boolean discardEventsAfterCancel) {
        TestPublisher<Buffer> payload = new TestPublisher<>();
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final StreamingHttpResponse response = reqRespFactory.ok().payloadBody(payload);
        responseSingle.onSuccess(response);

        verifyNoInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();
        assert subscriber.response != null;
        subscriber.response.payloadBody().forEach(chunk -> {
            //ignore
        });

        payload.onComplete();

        verify(beforeFinally).onComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] discardEventsAfterCancel={0} payloadError={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void resubscribeToPayloadBody(boolean discardEventsAfterCancel, boolean payloadError) {
        LegacyTestSingle<StreamingHttpResponse> responseSingle = new LegacyTestSingle<>(true);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        toSource(responseSingle
                .liftSync(new BeforeFinallyHttpOperator(beforeFinally, discardEventsAfterCancel)))
                .subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        PublisherSource.Processor<Buffer, Buffer> payload = Processors.newPublisherProcessor();
        final StreamingHttpResponse response = reqRespFactory.ok().payloadBody(fromSource(payload));
        responseSingle.onSuccess(response);

        verifyNoInteractions(beforeFinally);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();
        assert subscriber.response != null;

        // Subscribe for the first time.
        TestPublisherSubscriber<Buffer> payloadSubscriber1 = new TestPublisherSubscriber<>();
        toSource(subscriber.response.payloadBody()).subscribe(payloadSubscriber1);
        payloadSubscriber1.awaitSubscription().request(MAX_VALUE);
        if (payloadError) {
            payload.onError(DELIBERATE_EXCEPTION);
            verify(beforeFinally).onError(DELIBERATE_EXCEPTION);
            assertThat(payloadSubscriber1.awaitOnError(), is(sameInstance(DELIBERATE_EXCEPTION)));
        } else {
            payload.onComplete();
            verify(beforeFinally).onComplete();
            payloadSubscriber1.awaitOnComplete();
        }

        // Subscribe for the second time.
        TestPublisherSubscriber<Buffer> payloadSubscriber2 = new TestPublisherSubscriber<>();
        toSource(subscriber.response.payloadBody()).subscribe(payloadSubscriber2);
        payloadSubscriber2.awaitSubscription().request(MAX_VALUE);
        assertThat(payloadSubscriber2.awaitOnError(), is(instanceOf(DuplicateSubscribeException.class)));
        verify(beforeFinally).onError(any(DuplicateSubscribeException.class));
    }

    private static final class ResponseSubscriber implements SingleSource.Subscriber<StreamingHttpResponse> {

        @Nullable
        Cancellable cancellable;
        @Nullable
        private StreamingHttpResponse response;
        private boolean responseReceived;
        @Nullable
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

        void verifyNoResponseReceived() {
            assertThat("Response received unexpectedly.", responseReceived, is(false));
            assertThat("Unexpected response.", response, is(nullValue()));
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
