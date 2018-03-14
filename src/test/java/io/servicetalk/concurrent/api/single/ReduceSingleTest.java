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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.PublisherRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Verifier;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class ReduceSingleTest {
    @Rule
    public final MockedSingleListenerRule<String> listenerRule = new MockedSingleListenerRule<>();

    @Rule
    public final PublisherRule<String> publisherRule = new PublisherRule<>();

    @Rule
    public final ReducerRule reducerRule = new ReducerRule();

    @Test
    public void testSingleItem() throws Exception {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.sendItems("Hello").complete();
        listenerRule.verifySuccess("Hello");
    }

    @Test
    public void testEmpty() throws Exception {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.complete();
        listenerRule.verifySuccess(""); // Empty string as exactly one item is required.
    }

    @Test
    public void testMultipleItems() throws Exception {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.sendItems("Hello1", "Hello2", "Hello3").complete();
        listenerRule.verifySuccess("Hello1Hello2Hello3");
    }

    @Test
    public void testError() throws Exception {
        reducerRule.listen(publisherRule, listenerRule);
        publisherRule.fail();
        listenerRule.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testFactoryReturnsNull() throws Exception {
        listenerRule.listen(publisherRule.getPublisher().reduce(() -> null, (o, s) -> o));
        publisherRule.sendItems("foo").complete();
        listenerRule.verifySuccess(null);
    }

    @Test
    public void testAggregatorReturnsNull() throws Exception {
        listenerRule.listen(publisherRule.getPublisher().reduce(() -> "", (o, s) -> null));
        publisherRule.sendItems("foo").complete();
        listenerRule.verifySuccess(null);
    }

    @Test
    public void testReducerExceptionCleanup() {
        final RuntimeException testException = new RuntimeException("fake exception");
        listenerRule.listen(publisherRule.getPublisher().reduce(() -> "", new BiFunction<String, String, String>() {
            private int callNumber;
            @Override
            public String apply(String o, String s) {
                if (++callNumber == 2) {
                    throw testException;
                }
                return o + s;
            }
        }));

        verifyThrows(cause -> assertSame(testException, cause));
    }

    private void verifyThrows(Consumer<Throwable> assertFunction) {
        try {
            publisherRule.sendItems("Hello1", "Hello2", "Hello3");
            fail();
        } catch (Throwable cause) {
            assertFunction.accept(cause);
            // Now simulate failing the publisher by emit onError(...)
            publisherRule.fail(false, cause);
            listenerRule.verifyFailure(cause);
        }
    }

    private static class ReducerRule extends Verifier {

        ReducerRule listen(PublisherRule<String> publisherRule, MockedSingleListenerRule<String> listenerRule) {
            listenerRule.listen(publisherRule.getPublisher().reduce(() -> "", (r, s) -> r + s));
            return this;
        }
    }
}
