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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.NoSuchElementException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class PubFirstOrErrorTest {
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor();
    private final TestSingleSubscriber<String> listenerRule = new TestSingleSubscriber<>();
    private final TestPublisher<String> publisher = new TestPublisher<>();

    @Test
    void syncSingleItemCompleted() {
        toSource(from("hello").firstOrError()).subscribe(listenerRule);
        assertThat(listenerRule.awaitOnSuccess(), is("hello"));
    }

    @Test
    void syncMultipleItemCompleted() {
        toSource(from("foo", "bar").firstOrError()).subscribe(listenerRule);
        assertThat(listenerRule.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void asyncSingleItemCompleted() throws Exception {
        toSource(publisher.firstOrError()).subscribe(listenerRule);
        executorExtension.executor().submit(() -> {
            publisher.onNext("hello");
            publisher.onComplete();
        }).toFuture().get();
        assertThat(listenerRule.awaitOnSuccess(), is("hello"));
    }

    @Test
    void asyncMultipleItemCompleted() throws Exception {
        toSource(publisher.firstOrError()).subscribe(listenerRule);
        executorExtension.executor().submit(() -> {
            publisher.onNext("foo", "bar");
            publisher.onComplete();
        }).toFuture().get();
        assertThat(listenerRule.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void singleItemNoComplete() {
        toSource(publisher.firstOrError()).subscribe(listenerRule);
        publisher.onNext("hello");
        assertThat(listenerRule.pollTerminal(10, MILLISECONDS), is(nullValue()));
    }

    @Test
    void singleItemErrorPropagates() {
        toSource(publisher.firstOrError()).subscribe(listenerRule);
        publisher.onNext("hello");
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(listenerRule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void noItemsFails() {
        toSource(publisher.firstOrError()).subscribe(listenerRule);
        publisher.onComplete();
        assertThat(listenerRule.awaitOnError(), instanceOf(NoSuchElementException.class));
    }

    @Test
    void noItemErrorPropagates() {
        toSource(publisher.firstOrError()).subscribe(listenerRule);
        publisher.onError(DELIBERATE_EXCEPTION);
        assertThat(listenerRule.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void multipleItemsFails() {
        toSource(publisher.firstOrError()).subscribe(listenerRule);
        publisher.onNext("foo", "bar");
        publisher.onComplete();
        assertThat(listenerRule.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }
}
