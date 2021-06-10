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

import org.junit.Test;

import java.util.Arrays;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;

public class PublishAndSubscribeOnTest extends AbstractPublishAndSubscribeOnTest {

    @Test
    public void testNoOffload() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(0, Function.identity());
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads[ON_SUBSCRIBE_THREAD], appExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads[TERMINAL_THREAD], appExecutor);
    }

    @Test
    public void testPublishOn() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(
                2, // onSubscribe, onComplete
                c -> c.publishOn(offload.executor()));
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads[ON_SUBSCRIBE_THREAD], offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads[TERMINAL_THREAD], offloadExecutor);
    }

    @Test
    public void testSubscribeOn() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(
                1, // subscribe
                c -> c.subscribeOn(offload.executor()));
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads[ON_SUBSCRIBE_THREAD], offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads[TERMINAL_THREAD], appExecutor);
    }

    @Test
    public void testPublishAndSubscribeOn() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(
                3, // subscribe, onSubscribe, onComplete
                c -> c.publishAndSubscribeOn(offload.executor()));
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads[ON_SUBSCRIBE_THREAD], offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads[TERMINAL_THREAD], offloadExecutor);
    }

    @Test
    public void testSubscribeOnWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupAndCancel(
                2, // subscribe, cancel
                c -> c.subscribeOn(offload.executor()));
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads[ON_SUBSCRIBE_THREAD], offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads[TERMINAL_THREAD], offloadExecutor);
    }

    @Test
    public void testPublishAndSubscribeOnWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupAndCancel(
                3, // subscribe, onSubscribe, cancel
                c -> c.publishAndSubscribeOn(offload.executor()));
        String threads = Arrays.toString(capturedThreads);
        assertThat("Unexpected executor for subscribe " + threads,
                capturedThreads[ON_SUBSCRIBE_THREAD], offloadExecutor);
        assertThat("Unexpected executor for complete " + threads,
                capturedThreads[TERMINAL_THREAD], offloadExecutor);
    }
}
