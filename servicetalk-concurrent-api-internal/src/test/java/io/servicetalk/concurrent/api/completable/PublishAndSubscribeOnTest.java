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

import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

public class PublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    @Test
    public void testNoOffload() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(Function.identity());
        TypeSafeMatcher<Thread> appExecutor = matchPrefix(APP_EXECUTOR_PREFIX);
        TypeSafeMatcher<Thread> sourceExecutor = matchPrefix(SOURCE_EXECUTOR_PREFIX);
        assertThat("Unexpected executor for subscribe", capturedThreads.get(SUBSCRIBE_THREAD), appExecutor);
        assertThat("Unexpected executor for complete", capturedThreads.get(TERMINAL_THREAD), sourceExecutor);
    }

    @Test
    public void testPublishOn() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(
                c -> c.publishOn(offloader.executor()));
        TypeSafeMatcher<Thread> appExecutor = matchPrefix(APP_EXECUTOR_PREFIX);
        assertThat("Unexpected executor for subscribe", capturedThreads.get(SUBSCRIBE_THREAD), appExecutor);
        assertThat("Unexpected executor for complete", capturedThreads.get(TERMINAL_THREAD), not(appExecutor));
    }

    @Test
    public void testSubscribeOn() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(
                c -> c.subscribeOn(offloader.executor()));
        TypeSafeMatcher<Thread> appExecutor = matchPrefix(APP_EXECUTOR_PREFIX);
        assertThat("Unexpected executor for subscribe", capturedThreads.get(SUBSCRIBE_THREAD), not(appExecutor));
        assertThat("Unexpected executor for complete", capturedThreads.get(TERMINAL_THREAD), not(appExecutor));
    }

    @Test
    public void testPublishAndSubscribeOn() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndSubscribe(
                c -> c.publishAndSubscribeOn(offloader.executor()));
        TypeSafeMatcher<Thread> appExecutor = matchPrefix(APP_EXECUTOR_PREFIX);
        assertThat("Unexpected executor for subscribe", capturedThreads.get(SUBSCRIBE_THREAD), not(appExecutor));
        assertThat("Unexpected executor for complete", capturedThreads.get(TERMINAL_THREAD), not(appExecutor));
    }

    @Test
    public void testSubscribeOnWithCancel() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndCancel(
                c -> c.subscribeOn(offloader.executor()));
        TypeSafeMatcher<Thread> appExecutor = matchPrefix(APP_EXECUTOR_PREFIX);
        assertThat("Unexpected executor for subscribe", capturedThreads.get(SUBSCRIBE_THREAD), not(appExecutor));
        assertThat("Unexpected executor for complete", capturedThreads.get(TERMINAL_THREAD), appExecutor);
    }

    @Test
    public void testPublishAndSubscribeOnWithCancel() throws InterruptedException {
        AtomicReferenceArray<Thread> capturedThreads = setupAndCancel(
                c -> c.publishAndSubscribeOn(offloader.executor()));
        TypeSafeMatcher<Thread> appExecutor = matchPrefix(APP_EXECUTOR_PREFIX);
        assertThat("Unexpected executor for subscribe", capturedThreads.get(SUBSCRIBE_THREAD), not(appExecutor));
        assertThat("Unexpected executor for complete", capturedThreads.get(TERMINAL_THREAD), not(appExecutor));
    }
}
