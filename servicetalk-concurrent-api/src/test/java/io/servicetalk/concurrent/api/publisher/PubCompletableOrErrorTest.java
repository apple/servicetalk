/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PubCompletableOrErrorTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final TestCompletableSubscriber subscriber = new TestCompletableSubscriber();

    @Test
    public void noElementsCompleted() {
        toSource(empty().completableOrError()).subscribe(subscriber);
        subscriber.awaitOnComplete();
    }

    @Test
    public void noElementsError() {
        toSource(failed(DELIBERATE_EXCEPTION).completableOrError()).subscribe(subscriber);
        assertSame(DELIBERATE_EXCEPTION, subscriber.awaitOnError());
    }

    @Test
    public void oneElementsAlwaysFails() {
        toSource(from("foo").completableOrError()).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void twoElementsAlwaysFails() {
        // Use TestPublisher to force deliver two items, and verify the operator doesn't duplicate terminate.
        TestPublisher<String> publisher = new TestPublisher<>();
        toSource(publisher.completableOrError()).subscribe(subscriber);
        assertTrue(publisher.isSubscribed());
        publisher.onNext("foo", "bar");
        assertThat(subscriber.awaitOnError(), instanceOf(IllegalArgumentException.class));
    }
}
