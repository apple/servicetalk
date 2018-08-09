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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.concurrent.api.Single;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CompletableToSingleTest {
    @Rule
    public MockedSubscriberRule<String> subscriberRule = new MockedSubscriberRule<>();
    private static final String INITIAL_OBJECT = new String();

    @Test
    public void withoutSupplier() {
        final String expectedValue = "foo";
        Completable c = new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onComplete();
            }
        };
        AtomicReference<Object> actualValue = new AtomicReference<>(INITIAL_OBJECT);
        c.toSingle(expectedValue).subscribe(new Single.Subscriber<String>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
            }

            @Override
            public void onSuccess(@Nullable String result) {
                assertTrue(actualValue.compareAndSet(INITIAL_OBJECT, result));
            }

            @Override
            public void onError(Throwable t) {
                assertTrue(actualValue.compareAndSet(INITIAL_OBJECT, t));
            }
        });
        assertSame(expectedValue, actualValue.get());
    }

    @Test
    public void withSupplier() {
        final Supplier<String> expectedValueSupplier = () -> "foo";
        Completable c = new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onComplete();
            }
        };
        AtomicReference<Object> actualValue = new AtomicReference<>(INITIAL_OBJECT);
        c.toSingle(expectedValueSupplier).subscribe(new Single.Subscriber<String>() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
            }

            @Override
            public void onSuccess(@Nullable String result) {
                assertTrue(actualValue.compareAndSet(INITIAL_OBJECT, result));
            }

            @Override
            public void onError(Throwable t) {
                assertTrue(actualValue.compareAndSet(INITIAL_OBJECT, t));
            }
        });
        assertSame(expectedValueSupplier.get(), actualValue.get());
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        subscriberRule.subscribe(Completable.completed().toSingle(() -> {
            throw DELIBERATE_EXCEPTION;
        })).request(1).verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void nullSupplierInTerminalSucceeds() {
        subscriberRule.subscribe(Completable.completed().toSingle(() -> null))
                .request(1).verifySuccess();
    }

    @Test
    public void nullInTerminalSucceeds() {
        subscriberRule.subscribe(Completable.completed().toSingle((String) null))
                .request(1).verifySuccess();
    }

    @Test
    public void noTerminalSucceeds() {
        subscriberRule.subscribe(Completable.completed().toSingle())
                .request(1).verifySuccess();
    }
}
