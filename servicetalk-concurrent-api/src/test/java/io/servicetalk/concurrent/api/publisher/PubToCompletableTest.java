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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.DeferredEmptySubscription;
import io.servicetalk.concurrent.api.MockedCompletableListenerRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;

public class PubToCompletableTest {

    @Rule
    public final MockedCompletableListenerRule listenerRule = new MockedCompletableListenerRule();

    @Test
    public void testSuccess() {
        listen(Publisher.just("Hello")).verifyCompletion();
    }

    @Test
    public void testError() {
        listen(Publisher.error(DELIBERATE_EXCEPTION)).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testEmpty() {
        listen(Publisher.empty()).verifyCompletion();
    }

    @Test
    public void testEmptyFromRequestN() {
        listen(new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new DeferredEmptySubscription(subscriber, complete()));
            }
        }).verifyCompletion();
    }

    @Test
    public void testErrorFromRequestN() {
        listen(new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new DeferredEmptySubscription(subscriber, TerminalNotification.error(DELIBERATE_EXCEPTION)));
            }
        }).verifyFailure(DELIBERATE_EXCEPTION);
    }

    private MockedCompletableListenerRule listen(Publisher<String> src) {
        return listenerRule.listen(src.ignoreElements());
    }
}
