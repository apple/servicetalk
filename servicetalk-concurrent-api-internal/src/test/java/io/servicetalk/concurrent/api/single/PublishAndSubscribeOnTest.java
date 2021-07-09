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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;

class PublishAndSubscribeOnTest extends AbstractSinglePublishAndSubscribeOnTest {

    @Test
    void testPublishOn() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Single::publishOn, offload.executor());
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected threads for original source " + threads,
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], APP_EXECUTOR);
        assertThat("Unexpected threads for offloaded source " + threads,
                capturedThreads[TERMINAL_SIGNAL_THREAD], OFFLOAD_EXECUTOR);
    }

    @Test
    void testSubscribeOn() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(1, // subscribe
                Single::subscribeOn, offload.executor());
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected threads for original source " + threads,
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], APP_EXECUTOR);
        assertThat("Unexpected threads for offloaded source " + threads,
                capturedThreads[TERMINAL_SIGNAL_THREAD], APP_EXECUTOR);
    }

    @Test
    void testSubscribeOnWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupForCancelAndSubscribe(Single::subscribeOn, offload.executor());
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected threads for original source " + threads,
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], OFFLOAD_EXECUTOR);
        assertThat("Unexpected threads for offloaded source " + threads,
                capturedThreads[TERMINAL_SIGNAL_THREAD], OFFLOAD_EXECUTOR);
    }
}
