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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Publisher;

import org.junit.Test;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PublishAndSubscribeOnDisableOffloadTest extends AbstractPublishAndSubscribeOnTest {

    @Test
    public void testPublishOnDisable() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::publishOnOverride, immediate());

        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testSubscribeOnDisable() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Publisher::subscribeOnOverride, immediate());

        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                is(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));
    }

    @Test
    public void testPublishAndSubscribeOnDisable() throws InterruptedException {
        Thread[] capturedThreads =
                setupAndSubscribe(Publisher::publishAndSubscribeOnOverride, immediate());

        assertThat("Unexpected threads for subscription and subscriber for offloaded source.",
                capturedThreads[OFFLOADED_SUBSCRIBER_THREAD],
                is(capturedThreads[OFFLOADED_SUBSCRIPTION_THREAD]));

        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }
}
