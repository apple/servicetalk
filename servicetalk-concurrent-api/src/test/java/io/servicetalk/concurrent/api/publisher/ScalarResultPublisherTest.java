/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.TestPublisherSubscriber;

import org.junit.Test;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ScalarResultPublisherTest {

    private TestPublisherSubscriber<String> subscriber = new TestPublisherSubscriber<>();

    @Test
    public void testJust() {
        toSource(from("Hello")).subscribe(subscriber);
        subscriber.request(1);
        assertThat(subscriber.takeItems(), contains("Hello"));
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testError() {
        toSource(Publisher.<String>error(DELIBERATE_EXCEPTION)).subscribe(subscriber);
        assertThat(subscriber.takeError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testEmpty() {
        toSource(Publisher.<String>empty()).subscribe(subscriber);
        assertThat(subscriber.takeTerminal(), is(complete()));
    }

    @Test
    public void testNever() {
        toSource(Publisher.<String>never()).subscribe(subscriber);
        assertTrue(subscriber.subscriptionReceived());
        assertThat(subscriber.takeItems(), hasSize(0));
        assertThat(subscriber.takeTerminal(), nullValue());
    }
}
