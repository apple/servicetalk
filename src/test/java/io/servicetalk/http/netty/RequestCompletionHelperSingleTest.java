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
package io.servicetalk.http.netty;

import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSingle;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpResponse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class RequestCompletionHelperSingleTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void nullAsSuccess() {
        final RequestConcurrencyController controller = mock(RequestConcurrencyController.class);
        RequestCompletionHelperSingle helper = new RequestCompletionHelperSingle(success(null), controller);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        helper.subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));
        verify(controller).requestFinished();

        subscriber.verifyNullResponseReceived();
        verifyNoMoreInteractions(controller);
    }

    @Test
    public void duplicateOnSuccess() {
        AtomicReference<Single.Subscriber<? super HttpResponse<HttpPayloadChunk>>> subRef = new AtomicReference<>();

        Single<HttpResponse<HttpPayloadChunk>> original = new Single<HttpResponse<HttpPayloadChunk>>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpResponse<HttpPayloadChunk>> subscriber) {
                subRef.set(subscriber);
                subscriber.onSubscribe(IGNORE_CANCEL);
            }
        };

        final RequestConcurrencyController controller = mock(RequestConcurrencyController.class);
        RequestCompletionHelperSingle helper = new RequestCompletionHelperSingle(original, controller);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        helper.subscribe(subscriber);
        assertThat("Original Single not subscribed.", subRef.get(), is(notNullValue()));
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final HttpResponse<HttpPayloadChunk> response = newResponse(OK);
        subRef.get().onSuccess(response);
        final HttpResponse<HttpPayloadChunk> received = subscriber.verifyResponseReceived();
        verifyZeroInteractions(controller);

        final HttpResponse<HttpPayloadChunk> response2 = newResponse(OK);

        subRef.get().onSuccess(response2);
        final HttpResponse<HttpPayloadChunk> received2 = subscriber.verifyResponseReceived();
        // Old response should be preserved.
        assertThat("Duplicate response received.", received2, is(received));
        verifyZeroInteractions(controller);
    }

    @Test
    public void cancelBeforeOnSuccess() throws ExecutionException, InterruptedException {
        TestSingle<HttpResponse<HttpPayloadChunk>> responseSingle = new TestSingle<>(true);
        final RequestConcurrencyController controller = mock(RequestConcurrencyController.class);
        RequestCompletionHelperSingle helper = new RequestCompletionHelperSingle(responseSingle, controller);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        helper.subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        subscriber.cancellable.cancel();
        verify(controller).requestFinished();
        responseSingle.verifyCancelled();

        final HttpResponse<HttpPayloadChunk> response = newResponse(OK);
        responseSingle.onSuccess(response);

        subscriber.verifyResponseReceived();
        verifyNoMoreInteractions(controller);
        assert subscriber.response != null;
        expectedException.expect(instanceOf(ExecutionException.class));
        expectedException.expectCause(instanceOf(CancellationException.class));
        awaitIndefinitely(subscriber.response.getPayloadBody());
    }

    @Test
    public void cancelBeforeOnError() {
        TestSingle<HttpResponse<HttpPayloadChunk>> responseSingle = new TestSingle<>(true);
        final RequestConcurrencyController controller = mock(RequestConcurrencyController.class);
        RequestCompletionHelperSingle helper = new RequestCompletionHelperSingle(responseSingle, controller);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        helper.subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        subscriber.cancellable.cancel();
        verify(controller).requestFinished();
        responseSingle.verifyCancelled();

        responseSingle.onError(DELIBERATE_EXCEPTION);
        assertThat("onError called post cancel.", subscriber.error, is(DELIBERATE_EXCEPTION));
        verifyNoMoreInteractions(controller);
    }

    @Test
    public void cancelAfterOnSuccess() {
        TestSingle<HttpResponse<HttpPayloadChunk>> responseSingle = new TestSingle<>(true);
        final RequestConcurrencyController controller = mock(RequestConcurrencyController.class);
        RequestCompletionHelperSingle helper = new RequestCompletionHelperSingle(responseSingle, controller);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        helper.subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final HttpResponse<HttpPayloadChunk> response = newResponse(OK, never());
        responseSingle.onSuccess(response);

        verifyZeroInteractions(controller);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();

        subscriber.cancellable.cancel();
        verifyZeroInteractions(controller);
        // We unconditionally cancel and let the original single handle the cancel post terminate
        responseSingle.verifyCancelled();
    }

    @Test
    public void cancelAfterOnError() {
        TestSingle<HttpResponse<HttpPayloadChunk>> responseSingle = new TestSingle<>(true);
        final RequestConcurrencyController controller = mock(RequestConcurrencyController.class);
        RequestCompletionHelperSingle helper = new RequestCompletionHelperSingle(responseSingle, controller);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        helper.subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        responseSingle.onError(DELIBERATE_EXCEPTION);

        verify(controller).requestFinished();
        assertThat("onError not called.", subscriber.error, is(DELIBERATE_EXCEPTION));

        subscriber.cancellable.cancel();
        verifyNoMoreInteractions(controller);
        // We unconditionally cancel and let the original single handle the cancel post terminate
        responseSingle.verifyCancelled();
    }

    @Test
    public void payloadComplete() {
        TestPublisher<HttpPayloadChunk> payload = new TestPublisher<>();
        payload.sendOnSubscribe();
        TestSingle<HttpResponse<HttpPayloadChunk>> responseSingle = new TestSingle<>(true);
        final RequestConcurrencyController controller = mock(RequestConcurrencyController.class);
        RequestCompletionHelperSingle helper = new RequestCompletionHelperSingle(responseSingle, controller);
        final ResponseSubscriber subscriber = new ResponseSubscriber();
        helper.subscribe(subscriber);
        assertThat("onSubscribe not called.", subscriber.cancellable, is(notNullValue()));

        final HttpResponse<HttpPayloadChunk> response = newResponse(OK, payload);
        responseSingle.onSuccess(response);

        verifyZeroInteractions(controller);
        responseSingle.verifyNotCancelled();
        subscriber.verifyResponseReceived();
        assert subscriber.response != null;
        subscriber.response.getPayloadBody().forEach(chunk -> {
            //ignore
        });

        payload.onComplete();

        verify(controller).requestFinished();
    }

    private static final class ResponseSubscriber implements Single.Subscriber<HttpResponse<HttpPayloadChunk>> {

        Cancellable cancellable;
        @Nullable
        private HttpResponse<HttpPayloadChunk> response;
        private boolean responseReceived;
        Throwable error;

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        @Override
        public void onSuccess(@Nullable final HttpResponse<HttpPayloadChunk> result) {
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
        HttpResponse<HttpPayloadChunk> verifyResponseReceived() {
            assertThat("Response not received.", responseReceived, is(true));
            assertThat("Unexpected response.", response, is(notNullValue()));
            return response;
        }
    }
}
