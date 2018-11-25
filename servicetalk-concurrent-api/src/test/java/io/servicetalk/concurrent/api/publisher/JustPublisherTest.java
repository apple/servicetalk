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

import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

public class JustPublisherTest {
    @Rule
    public final MockedSubscriberRule<String> subscriberRule = new MockedSubscriberRule<>();

    @Test
    public void exceptionInTerminalCallsOnError() {
        subscriberRule.subscribe(Publisher.just("foo"));
        // The mock action must be setup after subscribe, because this creates a new mock object internally.
        doAnswer(invocation -> {
            throw DELIBERATE_EXCEPTION;
        }).when(subscriberRule.getSubscriber()).onNext(any());
        subscriberRule.request(1).verifyItems("foo").verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void nullInTerminalSucceeds() {
        subscriberRule.subscribe(Publisher.just(null)).request(1).verifySuccess();
    }
}
