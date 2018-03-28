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

import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.NoSuchElementException;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;

public class PubToSingleTest {

    @Rule
    public final MockedSingleListenerRule<String> listenerRule = new MockedSingleListenerRule<>();
    @Rule
    public final PublisherRule<String> publisher = new PublisherRule<>();

    @Test
    public void testSuccess() throws Exception {
        listen(Publisher.just("Hello")).verifySuccess("Hello");
    }

    @Test
    public void testError() throws Exception {
        listen(Publisher.error(DELIBERATE_EXCEPTION)).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testEmpty() throws Exception {
        listen(Publisher.empty()).verifyFailure(NoSuchElementException.class);
    }

    @Test
    public void testCancelled() throws Exception {
        listen(publisher.getPublisher());
        publisher.sendItems("Hello");
        listenerRule.verifySuccess("Hello");
        publisher.verifyCancelled();
    }

    @Test
    public void testErrorPostEmit() throws Exception {
        listen(publisher.getPublisher());
        publisher.sendItems("Hello");
        listenerRule.verifySuccess("Hello");
        publisher.fail(true);
        listenerRule.noMoreInteractions();
    }

    @Test
    public void testCompletePostEmit() throws Exception {
        listen(publisher.getPublisher());
        publisher.sendItems("Hello");
        listenerRule.verifySuccess("Hello");
        publisher.complete(true);
        listenerRule.noMoreInteractions();
    }

    private MockedSingleListenerRule<String> listen(Publisher<String> src) {
        return listenerRule.listen(src.first());
    }
}
