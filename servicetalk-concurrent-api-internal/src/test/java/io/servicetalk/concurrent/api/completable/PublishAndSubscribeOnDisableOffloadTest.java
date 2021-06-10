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

import io.servicetalk.concurrent.api.Completable;

import org.junit.Test;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static org.hamcrest.MatcherAssert.assertThat;

public class PublishAndSubscribeOnDisableOffloadTest extends AbstractCompletablePublishAndSubscribeOnTest {

    @Test
    public void testDefault() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe((c, e) -> c, immediate());
        assertThat("Unexpected executor for subscribe", capturedThreads[ON_SUBSCRIBE_THREAD], APP_EXECUTOR);
        assertThat("Unexpected executor for complete", capturedThreads[TERMINAL_SIGNAL_THREAD], APP_EXECUTOR);
    }

    @Test
    public void testPublishOnDisable() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Completable::publishOnOverride, immediate());
        assertThat("Unexpected executor for subscribe", capturedThreads[ON_SUBSCRIBE_THREAD], APP_EXECUTOR);
        assertThat("Unexpected executor for complete", capturedThreads[TERMINAL_SIGNAL_THREAD], APP_EXECUTOR);
    }

    @Test
    public void testSubscribeOnDisable() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Completable::subscribeOnOverride, immediate());
        assertThat("Unexpected executor for subscribe", capturedThreads[ON_SUBSCRIBE_THREAD], APP_EXECUTOR);
        assertThat("Unexpected executor for complete", capturedThreads[TERMINAL_SIGNAL_THREAD], APP_EXECUTOR);
    }

    @Test
    public void testPublishAndSubscribeOnDisable() throws InterruptedException {
        Thread[] capturedThreads = setupAndSubscribe(Completable::publishAndSubscribeOnOverride, immediate());
        assertThat("Unexpected executor for subscribe", capturedThreads[ON_SUBSCRIBE_THREAD], APP_EXECUTOR);
        assertThat("Unexpected executor for complete", capturedThreads[TERMINAL_SIGNAL_THREAD], APP_EXECUTOR);
    }

    @Test
    public void testPublishAndSubscribeOnDisableWithCancel() throws InterruptedException {
        Thread[] capturedThreads = setupAndCancel(Completable::publishAndSubscribeOnOverride, immediate());
        assertThat("Unexpected executor for subscribe", capturedThreads[ON_SUBSCRIBE_THREAD], APP_EXECUTOR);
        assertThat("Unexpected executor for complete", capturedThreads[TERMINAL_SIGNAL_THREAD], APP_EXECUTOR);
    }
}
