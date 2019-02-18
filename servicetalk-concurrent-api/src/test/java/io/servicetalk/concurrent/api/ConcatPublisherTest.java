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
package io.servicetalk.concurrent.api;

import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class ConcatPublisherTest {

    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();
    @Rule
    public final PublisherRule<String> first = new PublisherRule<>();
    @Rule
    public final PublisherRule<String> second = new PublisherRule<>();

    @Test
    public void testEnoughRequests() {
        Publisher<String> p = first.publisher().concatWith(second.publisher());
        subscriber.subscribe(p).request(2);
        first.sendItems("Hello1", "Hello2").complete();
        subscriber.request(2);
        second.sendItems("Hello3", "Hello4").complete();
        subscriber.verifySuccess("Hello1", "Hello2", "Hello3", "Hello4");
    }

    @Test
    public void testFirstEmitsError() {
        Publisher<String> p = first.publisher().concatWith(second.publisher());
        subscriber.subscribe(p).request(2);
        first.sendItems("Hello1", "Hello2").fail();
        subscriber.verifyItems("Hello1", "Hello2").verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSecondEmitsError() {
        Publisher<String> p = first.publisher().concatWith(second.publisher());
        subscriber.subscribe(p).request(2);
        first.sendItems("Hello1", "Hello2").complete();
        second.fail();
        subscriber.verifyItems("Hello1", "Hello2").verifyFailure(DELIBERATE_EXCEPTION);
    }
}
