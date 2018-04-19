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

import io.servicetalk.concurrent.internal.FlowControlUtil;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLong;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class PublisherRule<T> implements TestRule {

    private Publisher<T> source;
    private Subscriber<? super T> capturedSubscriber;
    private Subscription subscription;
    private volatile boolean cancelled;
    private AtomicLong outstandingRequest;
    private boolean deferOnSubscribe;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                outstandingRequest = new AtomicLong();
                subscription = new Subscription() {
                    @Override
                    public void request(long n) {
                        outstandingRequest.accumulateAndGet(n, FlowControlUtil::addWithOverflowProtection);
                    }

                    @Override
                    public void cancel() {
                        cancelled = true;
                    }
                };
                source = new Publisher<T>(immediate()) {
                    @Override
                    protected void handleSubscribe(Subscriber<? super T> s) {
                        capturedSubscriber = s;
                        if (!deferOnSubscribe) {
                            s.onSubscribe(subscription);
                        }
                    }
                };
                base.evaluate();
            }
        };
    }

    public Publisher<T> getPublisher() {
        return getPublisher(false);
    }

    public Publisher<T> getPublisher(boolean deferOnSubscribe) {
        this.deferOnSubscribe = deferOnSubscribe;
        return source;
    }

    public void sendOnSubscribe() {
        assert deferOnSubscribe;
        deferOnSubscribe = false;
        capturedSubscriber.onSubscribe(subscription);
    }

    public PublisherRule<T> sendItems(T... items) {
        verifySubscriber(false);
        return sendItemsNoVerify(items);
    }

    public PublisherRule<T> sendItemsNoVerify(T... items) {
        assertThat("Subscriber has not requested enough.", outstandingRequest.get(), is(greaterThanOrEqualTo((long) items.length)));
        for (T item : items) {
            outstandingRequest.decrementAndGet();
            capturedSubscriber.onNext(item);
        }
        return this;
    }

    public PublisherRule<T> fail(boolean mayBeCancelled) {
        fail(mayBeCancelled, DELIBERATE_EXCEPTION);
        return this;
    }

    public PublisherRule<T> fail(boolean mayBeCancelled, Throwable cause) {
        verifySubscriber(mayBeCancelled);
        capturedSubscriber.onError(cause);
        return this;
    }

    public PublisherRule<T> fail() {
        return fail(false);
    }

    public PublisherRule<T> complete(boolean mayBeCancelled) {
        verifySubscriber(mayBeCancelled);
        capturedSubscriber.onComplete();
        return this;
    }

    public PublisherRule<T> complete() {
        return complete(false);
    }

    public PublisherRule<T> verifyCancelled() {
        verifySubscriber(true);
        return this;
    }

    public PublisherRule<T> verifyNotCancelled() {
        verifySubscriber(false);
        return this;
    }

    private void verifySubscriber(boolean expectCancelled) {
        assertThat("No subscriber found.", capturedSubscriber, is(notNullValue()));
        assertThat("Unexpected cancel.", cancelled, is(expectCancelled));
    }
}
