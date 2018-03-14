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

import io.servicetalk.concurrent.internal.TerminalNotification;
import org.hamcrest.Matcher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * A {@link Subscriber} that will block in {@link #onNext(Object)}, {@link #onError(Throwable)} and {@link #onComplete()}
 * unless released via {@link #unblock(Object)} or {@link #unblockAll()}.
 */
public final class BlockingSubscriber<T> implements Subscriber<T> {

    @Nullable private Subscription s;

    private final BlockingQueue<Result> awaitingResults = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedQueue<T> received = new ConcurrentLinkedQueue<>();
    private volatile TerminalNotification terminal;

    @Override
    public void onSubscribe(Subscription s) {
        this.s = s;
    }

    @Override
    public void onNext(T t) {
        Result result = new Result(t);
        awaitingResults.add(result);
        try {
            result.awaitResult();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        terminal = error(t);
    }

    @Override
    public void onComplete() {
        terminal = complete();
    }

    public BlockingSubscriber<T> request(long request) {
        Subscription subscription = s;
        assert subscription != null : "Subscription can not be null.";
        subscription.request(request);
        return this;
    }

    public BlockingSubscriber<T> awaitAndVerifyAwaitingItem(T item) throws InterruptedException {
        if (awaitingResults.isEmpty()) {
            // Wait for an item and put it back if found. Order does not matter for awaiting items.
            awaitingResults.add(awaitingResults.take());
        } else {
            verifyAwaitingItems(item);
        }
        return this;
    }

    @SafeVarargs
    public final BlockingSubscriber<T> verifyAwaitingItems(T... items) {
        if (items.length == 0) {
            assertThat("Unexpected blocked state.", awaitingResults, hasSize(0));
        } else {
            List<T> results = awaitingResults.stream().map(Result::getExpectedResult).collect(toList());
            assertThat("Unexpected blocked state.", results, containsInAnyOrder(items));
        }
        return this;
    }

    public BlockingSubscriber<T> unblockAll() {
        for (Iterator<Result> iterator = awaitingResults.iterator(); iterator.hasNext();) {
            Result awaitingResult = iterator.next();
            iterator.remove();
            awaitingResult.completeSingle();
        }
        return this;
    }

    public BlockingSubscriber<T> unblock(T item) {
        requireNonNull(item);
        for (Iterator<Result> iterator = awaitingResults.iterator(); iterator.hasNext();) {
            Result awaitingResult = iterator.next();
            if (item.equals(awaitingResult.getExpectedResult())) {
                iterator.remove();
                awaitingResult.completeSingle();
                return this;
            }
        }
        throw new IllegalStateException("Item " + item + " not found in the awaiting results");
    }

    @SafeVarargs
    public final BlockingSubscriber<T> verifyReceived(T... items) {
        if (items.length == 0) {
            assertThat("Unexpected items received.", received, hasSize(0));
        } else {
            assertThat("Unexpected items received.", received, contains(items));
        }
        return this;
    }

    public BlockingSubscriber<T> verifyComplete() {
        assertThat("onComplete not received.", terminal, is(notNullValue()));
        assertThat("onComplete not received.", terminal, is(complete()));
        return this;
    }

    public BlockingSubscriber<T> verifyError(Matcher<TerminalNotification> matcher) {
        assertThat("onError not received.", terminal, is(notNullValue()));
        assertThat("onError not received.", terminal, matcher);
        return this;
    }

    private final class Result {

        private final TestSingle<T> single;
        private final T result;

        Result(T result) {
            this.single = new TestSingle<>();
            this.result = result;
        }

        public T getExpectedResult() {
            return result;
        }

        void completeSingle() {
            received.add(result);
            single.onSuccess(result);
        }

        @Nullable
        T awaitResult() throws ExecutionException, InterruptedException {
            return awaitIndefinitely(single);
        }
    }
}
