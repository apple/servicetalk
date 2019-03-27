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

import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.NoSuchElementException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class PubFirstOrErrorTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final LegacyMockedSingleListenerRule<String> listenerRule = new LegacyMockedSingleListenerRule<>();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();
    private final TestPublisher<String> publisher = new TestPublisher<>();

    @Test
    public void syncSingleItemCompleted() {
        listenerRule.listen(from("hello").firstOrError()).verifySuccess("hello");
    }

    @Test
    public void syncMultipleItemCompleted() {
        listenerRule.listen(Publisher.from("foo", "bar").firstOrError())
                .verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void asyncSingleItemCompleted() throws Exception {
        listenerRule.listen(publisher.firstOrError());
        executorRule.executor().submit(() -> {
            publisher.onNext("hello");
            publisher.onComplete();
        }).toFuture().get();
        listenerRule.verifySuccess("hello");
    }

    @Test
    public void asyncMultipleItemCompleted() throws Exception {
        listenerRule.listen(publisher.firstOrError());
        executorRule.executor().submit(() -> {
            publisher.onNext("foo", "bar");
            publisher.onComplete();
        }).toFuture().get();
        listenerRule.verifyFailure(IllegalArgumentException.class);
    }

    @Test
    public void singleItemNoComplete() {
        listenerRule.listen(publisher.firstOrError());
        publisher.onNext("hello");
        listenerRule.verifyNoEmissions();
    }

    @Test
    public void singleItemErrorPropagates() {
        listenerRule.listen(publisher.firstOrError());
        publisher.onNext("hello");
        publisher.onError(DELIBERATE_EXCEPTION);
        listenerRule.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void noItemsFails() {
        listenerRule.listen(publisher.firstOrError());
        publisher.onComplete();
        listenerRule.verifyFailure(NoSuchElementException.class);
    }

    @Test
    public void noItemErrorPropagates() {
        listenerRule.listen(publisher.firstOrError());
        publisher.onError(DELIBERATE_EXCEPTION);
        listenerRule.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void multipleItemsFails() {
        listenerRule.listen(publisher.firstOrError());
        publisher.onNext("foo", "bar");
        publisher.onComplete();
        listenerRule.verifyFailure(IllegalArgumentException.class);
    }
}
