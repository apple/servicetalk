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

import io.servicetalk.concurrent.BlockingIterable;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PublisherConcatMapIterableTest {
    @Rule
    public final PublisherRule<List<String>> publisher = new PublisherRule<>();
    @Rule
    public final MockedSubscriberRule<String> subscriber = new MockedSubscriberRule<>();
    @Rule
    public final PublisherRule<BlockingIterable<String>> cancellablePublisher = new PublisherRule<>();

    @Test
    public void cancellableIterableIsCancelled() {
        cancellablePublisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        subscriber.request(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellablePublisher.sendItems(new TestIterableToBlockingIterable<>(asList("one", "two"),
                (time, unit) -> { }, (time, unit) -> { }, () -> cancelled.set(true)));
        subscriber.verifyItems("one");
        subscriber.verifyNoEmissions();
        subscriber.cancel();
        assertTrue(cancelled.get());
    }

    @Test
    public void justComplete() {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        verifyTermination(true);
    }

    @Test
    public void justFail() {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        verifyTermination(false);
    }

    @Test
    public void singleElementSingleValueThenSuccess() {
        singleElementSingleValue(true);
    }

    @Test
    public void singleElementSingleValueThenFail() {
        singleElementSingleValue(false);
    }

    private void singleElementSingleValue(boolean success) {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        subscriber.request(1);
        publisher.sendItems(singletonList("one"));
        subscriber.verifyItems("one");
        subscriber.verifyNoEmissions();

        verifyTermination(success);
    }

    @Test
    public void singleElementMultipleValuesDelayedRequestThenSuccess() {
        singleElementMultipleValuesDelayedRequest(true);
    }

    @Test
    public void singleElementMultipleValuesDelayedRequestThenFail() {
        singleElementMultipleValuesDelayedRequest(false);
    }

    private void singleElementMultipleValuesDelayedRequest(boolean success) {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        subscriber.request(1);
        publisher.sendItems(asList("one", "two"));
        subscriber.verifyItems("one");

        if (success) {
            publisher.complete();
        } else {
            publisher.fail();
        }

        subscriber.request(1);
        subscriber.verifyItems("two");

        if (success) {
            subscriber.verifySuccess();
        } else {
            subscriber.verifyFailure(DELIBERATE_EXCEPTION);
        }
        publisher.verifyNotCancelled();
    }

    @Test
    public void multipleElementsSingleValueThenSuccess() {
        multipleElementsSingleValue(true);
    }

    @Test
    public void multipleElementsSingleValueThenFail() {
        multipleElementsSingleValue(false);
    }

    private void multipleElementsSingleValue(boolean success) {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        subscriber.request(1);
        publisher.sendItems(singletonList("one"));
        subscriber.verifyItems("one");

        subscriber.request(1);
        publisher.sendItems(singletonList("two"));
        subscriber.verifyItems("two");

        verifyTermination(success);
    }

    @Test
    public void multipleElementsMultipleValuesThenSuccess() {
        multipleElementsMultipleValues(true);
    }

    @Test
    public void multipleElementsMultipleValuesThenFail() {
        multipleElementsMultipleValues(false);
    }

    private void multipleElementsMultipleValues(boolean success) {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        subscriber.request(1);
        publisher.sendItems(asList("one", "two"));
        subscriber.verifyItems("one");

        subscriber.request(1);
        subscriber.verifyItems("two");

        subscriber.request(1);
        publisher.sendItems(asList("three", "four"));
        subscriber.verifyItems("three");

        subscriber.request(1);
        subscriber.verifyItems("four");

        verifyTermination(success);
    }

    @Test
    public void cancelIsPropagated() {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        subscriber.request(1);
        publisher.sendItems(asList("one", "two"));
        subscriber.verifyItems("one");
        subscriber.cancel();
        publisher.verifyCancelled();
    }

    @Test
    public void requestWithEmptyIterableThenSuccess() {
        requestWithEmptyIterable(true);
    }

    @Test
    public void requestWithEmptyIterableThenFail() {
        requestWithEmptyIterable(false);
    }

    private void requestWithEmptyIterable(boolean success) {
        publisher.getPublisher().concatMapIterable(identity()).subscribe(subscriber.getSubscriber());
        subscriber.verifySubscribe();
        subscriber.request(1);
        subscriber.request(1);

        publisher.sendItems(asList("one", "two", "three"));
        subscriber.verifyItems("one", "two");
        try {
            publisher.sendItems(asList("four"));
            fail();
        } catch (Throwable expected) {
            // request(n) to the source isn't high enough
        }

        subscriber.request(1);
        subscriber.verifyItems("three");
        subscriber.request(1);
        publisher.sendItems(asList("four"));
        subscriber.verifyItems("four");
        verifyTermination(success);
    }

    private void verifyTermination(boolean success) {
        if (success) {
            publisher.complete();
            subscriber.verifySuccess();
        } else {
            publisher.fail();
            subscriber.verifyFailure(DELIBERATE_EXCEPTION);
        }
        publisher.verifyNotCancelled();
    }
}
