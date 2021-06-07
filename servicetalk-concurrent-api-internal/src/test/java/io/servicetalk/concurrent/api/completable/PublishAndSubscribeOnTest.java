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
package io.servicetalk.concurrent.api.completable;

import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;

public class PublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    @Test
    public void testNoOffload() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(0, Function.identity());
        Matcher<Thread> appExecutor = matchPrefix(APP_EXECUTOR_PREFIX);
        Matcher<Thread> sourceExecutor = matchPrefix(SOURCE_EXECUTOR_PREFIX);
        String threads = capturedThreadsToString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads.get(SUBSCRIBE_THREAD), appExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads.get(TERMINAL_THREAD), sourceExecutor);
    }

    @Test
    public void testPublishOn() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(2, // 2 subscribes
                c -> c.publishOn(offload.executor()));
        Matcher<Thread> offloadExecutor = matchPrefix(OFFLOAD_EXECUTOR_PREFIX);
        String threads = capturedThreadsToString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads.get(SUBSCRIBE_THREAD), offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads.get(TERMINAL_THREAD), offloadExecutor);
    }

    @Test
    public void testSubscribeOn() throws InterruptedException {
        System.out.println("subscribeOn");
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(1,
                c -> c.subscribeOn(offload.executor()));
        Matcher<Thread> offloadExecutor = matchPrefix(OFFLOAD_EXECUTOR_PREFIX);
        Matcher<Thread> sourceExecutor = matchPrefix(SOURCE_EXECUTOR_PREFIX);
        String threads = capturedThreadsToString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads.get(SUBSCRIBE_THREAD), offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads.get(TERMINAL_THREAD), sourceExecutor);
    }

    @Test
    public void testPublishAndSubscribeOn() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(3, // 2 subscribes + 1 publish
                c -> c.publishAndSubscribeOn(offload.executor()));
        Matcher<Thread> offloadExecutor = matchPrefix(OFFLOAD_EXECUTOR_PREFIX);
        String threads = capturedThreadsToString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads.get(SUBSCRIBE_THREAD), offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads.get(TERMINAL_THREAD), offloadExecutor);
    }

    @Test
    public void testSubscribeOnWithCancel() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndCancel(
                c -> c.subscribeOn(offload.executor()));
        Matcher<Thread> offloadExecutor = matchPrefix(OFFLOAD_EXECUTOR_PREFIX);
        String threads = capturedThreadsToString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads.get(SUBSCRIBE_THREAD), offloadExecutor);
        assertThat("Unexpected executor for cancel " + threads,
                capturedThreads.get(TERMINAL_THREAD), offloadExecutor);
    }

    @Test
    public void testPublishAndSubscribeOnWithCancel() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndCancel(
                c -> c.publishAndSubscribeOn(offload.executor()));
        Matcher<Thread> offloadExecutor = matchPrefix(OFFLOAD_EXECUTOR_PREFIX);
        String threads = capturedThreadsToString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads.get(SUBSCRIBE_THREAD), offloadExecutor);
        assertThat("Unexpected executor for cancel " + threads,
                capturedThreads.get(TERMINAL_THREAD), offloadExecutor);
    }
}
