/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestCancellable;
import io.servicetalk.concurrent.api.TestSingle;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Single.collectInOrder;
import static io.servicetalk.concurrent.api.Single.collectUnordered;
import static io.servicetalk.concurrent.api.Single.collectUnorderedDelayError;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectTest {

    @Test
    void collectInOrderVarArgsPreservesOrderWhenResultsCompleteOutOfOrder() throws Exception {
        TestSingle<Integer> first = new TestSingle<>();
        TestSingle<Integer> second = new TestSingle<>();
        Future<? extends Collection<Integer>> future = collectInOrder(2, first, second).toFuture();

        first.awaitSubscribed();
        second.awaitSubscribed();
        second.onSuccess(2);
        first.onSuccess(1);

        assertThat("Unexpected result.", future.get(), contains(1, 2));
    }

    @Test
    void collectInOrderVarArgsMaxConcurrency() throws Exception {
        TestSingle<Integer> first = new TestSingle<>();
        TestSingle<Integer> second = new TestSingle<>();
        TestSingle<Integer> third = new TestSingle<>();
        Future<? extends Collection<Integer>> future = collectInOrder(2, first, second, third).toFuture();

        first.awaitSubscribed();
        second.awaitSubscribed();
        assertThat("Third source subscribed before a slot was available.", third.isSubscribed(), is(false));
        second.onSuccess(2);
        assertThat("Third source subscribed before the preceding source completed.", third.isSubscribed(), is(false));
        first.onSuccess(1);
        third.awaitSubscribed();
        third.onSuccess(3);

        assertThat("Unexpected result.", future.get(), contains(1, 2, 3));
    }

    @Test
    void collectInOrderIterableSuccess() throws Exception {
        Collection<Integer> integers = collectInOrder(asList(succeeded(1), succeeded(2))).toFuture().get();
        assertThat("Unexpected result.", integers, contains(1, 2));
    }

    @Test
    void collectInOrderEmptyIterable() throws Exception {
        Collection<Integer> integers = Single.<Integer>collectInOrder(asList()).toFuture().get();
        assertThat("Unexpected result.", integers.isEmpty(), is(true));
    }

    @Test
    void collectInOrderEmptyVarArgs() throws Exception {
        Collection<Integer> integers = Single.<Integer>collectInOrder().toFuture().get();
        assertThat("Unexpected result.", integers.isEmpty(), is(true));
    }

    @Test
    void collectInOrderDeliversLaterErrorInOrderAndCancelsRemainingSources() throws Exception {
        TestSingle<Integer> first = new TestSingle<>();
        TestSingle<Integer> second = new TestSingle<>();
        TestCancellable thirdCancellable = new TestCancellable();
        TestSingle<Integer> third = new TestSingle.Builder<Integer>().disableAutoOnSubscribe().build(subscriber -> {
            subscriber.onSubscribe(thirdCancellable);
            return subscriber;
        });
        Future<? extends Collection<Integer>> future = collectInOrder(asList(first, second, third), 3).toFuture();

        first.awaitSubscribed();
        second.awaitSubscribed();
        third.awaitSubscribed();
        second.onError(DELIBERATE_EXCEPTION);
        assertThat("Later error was delivered before the preceding source.", future.isDone(), is(false));

        first.onSuccess(1);
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
        thirdCancellable.awaitCancelled();
    }

    @Test
    void collectInOrderIterableMaxConcurrency() throws Exception {
        TestSingle<Integer> first = new TestSingle<>();
        TestSingle<Integer> second = new TestSingle<>();
        TestSingle<Integer> third = new TestSingle<>();
        Future<? extends Collection<Integer>> future = collectInOrder(asList(first, second, third), 1).toFuture();

        first.awaitSubscribed();
        assertThat("Second source subscribed before the first completed.", second.isSubscribed(), is(false));
        first.onSuccess(1);
        second.awaitSubscribed();
        assertThat("Third source subscribed before the second completed.", third.isSubscribed(), is(false));
        second.onSuccess(2);
        third.awaitSubscribed();
        third.onSuccess(3);

        assertThat("Unexpected result.", future.get(), contains(1, 2, 3));
    }

    @Test
    void collectVarArgSuccess() throws Exception {
        Collection<Integer> integers = collectUnordered(succeeded(1), succeeded(2)).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    void collectVarArgMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        Collection<Integer> integers = collectUnordered(1, succeeded(1), succeeded(2)).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    void collectVarArgFailure() throws Exception {
        Future<? extends Collection<Integer>> future =
                collectUnordered(failed(DELIBERATE_EXCEPTION), succeeded(2)).toFuture();
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void collectVarArgDelayError() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true))).toFuture();
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
        assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void collectVarArgDelayErrorMaxConcurrency() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(1, failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true))).toFuture();
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
        assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void collectIterableSuccess() throws Exception {
        Collection<Integer> integers = collectUnordered(asList(succeeded(1), succeeded(2))).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    void collectIterableMaxConcurrencySuccess() throws Exception {
        // Just testing that the method works. As it uses existing operators, we don't require elaborate tests
        Collection<Integer> integers = collectUnordered(asList(succeeded(1), succeeded(2)), 1).toFuture().get();
        assertThat("Unexpected result.", integers, containsInAnyOrder(1, 2));
    }

    @Test
    void collectIterableFailure() throws Exception {
        Future<? extends Collection<Integer>> future =
                collectUnordered(asList(failed(DELIBERATE_EXCEPTION), succeeded(2))).toFuture();
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void collectIterableDelayError() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(asList(failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true)))).toFuture();
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
        assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void collectIterableDelayErrorMaxConcurrency() throws Exception {
        AtomicBoolean secondSubscribed = new AtomicBoolean();
        Future<? extends Collection<Integer>> future = collectUnorderedDelayError(asList(failed(DELIBERATE_EXCEPTION),
                succeeded(2).beforeOnSubscribe(__ -> secondSubscribed.set(true))), 1).toFuture();
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertThat("Second source not subscribed.", secondSubscribed.get(), is(true));
        assertThat("Unexpected exception.", e.getCause(), is(DELIBERATE_EXCEPTION));
    }
}
