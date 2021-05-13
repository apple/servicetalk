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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Publisher;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OffloadingTest extends AbstractPublisherOffloadingTest {

    @Test
    void testNoOffload() throws InterruptedException {
        int offloads = testOffloading((c, e) -> c, TerminalOperation.COMPLETE);
        assertThat("Unexpected offloads: none", offloads, CoreMatchers.is(0));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, nullValue());
    }

    @Test
    void testNoOffloadCancel() throws InterruptedException {
        int offloads = testOffloading((c, e) -> c, TerminalOperation.CANCEL);
        assertThat("Unexpected offloads: none", offloads, CoreMatchers.is(0));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, APP_EXECUTOR);
    }

    @Test
    void testNoOffloadError() throws InterruptedException {
        int offloads = testOffloading((c, e) -> c, TerminalOperation.ERROR);
        assertThat("Unexpected offloads: none", offloads, CoreMatchers.is(0));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, nullValue());
    }

    @Test
    void testPublishOn() throws InterruptedException {
        int offloads = testOffloading(Publisher::publishOn, TerminalOperation.COMPLETE);
        assertThat("Unexpected offloads: onSubscribe, onNext, onComplete", offloads, CoreMatchers.is(3));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, OFFLOAD_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, nullValue(Thread.class));
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, nullValue(Thread.class));
    }

    @Test
    void testPublishOnCancel() throws InterruptedException {
        int offloads = testOffloading(Publisher::publishOn, TerminalOperation.CANCEL);
        assertThat("Unexpected offloads: onSubscribe", offloads, CoreMatchers.is(1));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, APP_EXECUTOR);
    }

    @Test
    void testPublishOnError() throws InterruptedException {
        int offloads = testOffloading(Publisher::publishOn, TerminalOperation.ERROR);
        assertThat("Unexpected offloads: onSubscribe, onError", offloads, CoreMatchers.is(2));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, OFFLOAD_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, nullValue());
    }

    @Test
    void testSubscribeOn() throws InterruptedException {
        int offloads = testOffloading(Publisher::subscribeOn, TerminalOperation.COMPLETE);
        assertThat("Unexpected offloads: 2, subscribe, request", offloads, CoreMatchers.is(2));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, OFFLOAD_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, nullValue());
    }

    @Test
    void testSubscribeOnCancel() throws InterruptedException {
        int offloads = testOffloading(Publisher::subscribeOn, TerminalOperation.CANCEL);
        assertThat("Unexpected offloads: subscribe, request, cancel", offloads, CoreMatchers.is(3));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, OFFLOAD_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, OFFLOAD_EXECUTOR);
    }

    @Test
    void testSubscribeOnError() throws InterruptedException {
        int offloads = testOffloading(Publisher::subscribeOn, TerminalOperation.ERROR);
        assertThat("Unexpected offloads: subscribe, request", offloads, CoreMatchers.is(2));
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBE_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBE_THREAD, OFFLOAD_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIBER_THREAD, APP_EXECUTOR);
        capturedThreads.assertCaptured(CaptureSlot.ORIGINAL_SUBSCRIPTION_THREAD, nullValue());
        capturedThreads.assertCaptured(CaptureSlot.OFFLOADED_SUBSCRIPTION_THREAD, nullValue());
    }
}
