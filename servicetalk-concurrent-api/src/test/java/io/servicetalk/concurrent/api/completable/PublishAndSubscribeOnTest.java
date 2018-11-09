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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.ExecutorRule;

import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class PublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    @Rule
    public final ExecutorRule executorRule = new ExecutorRule();

    @Test
    public void testPublishOnNoOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(c -> c.publishOn(executorRule.getExecutor()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testPublishOnOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(c -> c.publishOnOverride(executorRule.getExecutor()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testSubscribeOnNoOverride() throws InterruptedException {
        Thread[] capturedThreads = setupForCancelAndSubscribe(c -> c.subscribeOn(executorRule.getExecutor()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testSubscribeOnOverride() throws InterruptedException {
        Thread[] capturedThreads = setupForCancelAndSubscribe(c -> c.subscribeOnOverride(executorRule.getExecutor()));
        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testNoOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(c -> c.publishAndSubscribeOn(executorRule.getExecutor()));

        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testOverride() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(c -> c.publishAndSubscribeOnOverride(executorRule.getExecutor()));

        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testNoOverrideWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupForCancelAndSubscribe(c -> c.publishAndSubscribeOn(executorRule.getExecutor()));

        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], not(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }

    @Test
    public void testOverrideWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupForCancelAndSubscribe(c ->
                c.publishAndSubscribeOnOverride(executorRule.getExecutor()));

        assertThat("Unexpected threads for original and offloaded source.",
                capturedThreads[ORIGINAL_SUBSCRIBER_THREAD], is(capturedThreads[OFFLOADED_SUBSCRIBER_THREAD]));
    }
}
