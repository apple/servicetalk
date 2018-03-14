/**
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

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;

public class TakeWhilePublisherTest {

    @Rule public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();
    @Rule public final PublisherRule<String> publisher = new PublisherRule<>();

    @Test
    public void testWhile() throws Exception {
        Publisher<String> p = publisher.getPublisher().takeWhile(s -> !s.equals("Hello3"));
        subscriber.subscribe(p);
        subscriber.request(4);
        publisher.sendItemsNoVerify("Hello1", "Hello2", "Hello3");
        subscriber.verifySuccess("Hello1", "Hello2");
        publisher.verifyCancelled();
    }

    @Test
    public void testWhileError() throws Exception {
        Publisher<String> p = publisher.getPublisher().takeWhile(s -> !s.equals("Hello3"));
        subscriber.subscribe(p);
        subscriber.request(1);
        publisher.sendItems("Hello1").fail();
        subscriber.verifyItems("Hello1").verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testWhileComplete() throws Exception {
        Publisher<String> p = publisher.getPublisher().takeWhile(s -> !s.equals("Hello3"));
        subscriber.subscribe(p);
        subscriber.request(1);
        publisher.sendItems("Hello1").complete();
        subscriber.verifyItems("Hello1");
    }

    @Test
    public void testSubCancelled() throws Exception {
        Publisher<String> p = publisher.getPublisher().takeWhile(s -> !s.equals("Hello3"));
        subscriber.subscribe(p);
        subscriber.request(3);
        publisher.sendItems("Hello1", "Hello2");
        subscriber.verifyItems("Hello1", "Hello2");
        subscriber.cancel();
        publisher.verifyCancelled();
    }
}
