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
package io.servicetalk.concurrent.api.single;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReferenceArray;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PublishAndSubscribeOnDisableOffloadTest extends AbstractPublishAndSubscribeOnTest {

    @Test
    public void testPublishOnDisable() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(c -> c.publishOnOverride(immediate()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD), is(capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD)));
    }

    @Test
    public void testSubscribeOnDisable() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(c -> c.subscribeOnOverride(immediate()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD), is(capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD)));
    }

    @Test
    public void testPublishAndSubscribeOnDisable() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(
                c -> c.publishAndSubscribeOnOverride(immediate()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD), is(capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD)));
    }

    @Test
    public void testPublishAndSubscribeOnDisableWithCancel() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupForCancelAndSubscribe(
                c -> c.publishAndSubscribeOnOverride(immediate()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads.get(ORIGINAL_SUBSCRIBER_THREAD), is(capturedThreads.get(OFFLOADED_SUBSCRIBER_THREAD)));
    }
}
