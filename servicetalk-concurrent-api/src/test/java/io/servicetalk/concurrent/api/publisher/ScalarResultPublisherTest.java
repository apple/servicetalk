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

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;

public class ScalarResultPublisherTest {

    @Rule
    public MockedSubscriberRule subscriberRule = new MockedSubscriberRule();

    @Test
    public void testJust() throws Exception {
        subscriberRule.subscribe(Publisher.just("Hello")).verifySuccess("Hello");
    }

    @Test
    public void testError() throws Exception {
        subscriberRule.subscribe(Publisher.error(DELIBERATE_EXCEPTION)).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testEmpty() throws Exception {
        subscriberRule.subscribe(Publisher.empty()).verifySuccess();
    }

    @Test
    public void testNever() throws Exception {
        subscriberRule.subscribe(Publisher.never()).verifyNoEmissions();
    }
}
