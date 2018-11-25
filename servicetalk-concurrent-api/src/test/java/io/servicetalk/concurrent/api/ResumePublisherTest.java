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

import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class ResumePublisherTest {

    @Rule
    public final MockedSubscriberRule<Integer> subscriber = new MockedSubscriberRule<>();

    private TestPublisher<Integer> first;
    private TestPublisher<Integer> second;

    @Before
    public void setUp() {
        first = new TestPublisher<>();
        first.sendOnSubscribe();
        second = new TestPublisher<>();
        second.sendOnSubscribe();
    }

    @Test
    public void testFirstComplete() {
        subscriber.subscribe(first.onErrorResume(throwable -> second));
        subscriber.request(1);
        first.sendItems(1).onComplete();
        subscriber.verifySuccess(1);
    }

    @Test
    public void testFirstErrorSecondComplete() {
        subscriber.subscribe(first.onErrorResume(throwable -> second));
        subscriber.request(1);
        first.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyNoEmissions();
        second.sendItems(1).onComplete();
        subscriber.verifySuccess(1);
    }

    @Test
    public void testFirstErrorSecondError() {
        subscriber.subscribe(first.onErrorResume(throwable -> second));
        subscriber.request(1);
        first.onError(new DeliberateException());
        subscriber.verifyNoEmissions();
        second.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelFirstActive() {
        subscriber.subscribe(first.onErrorResume(throwable -> second));
        subscriber.request(1);
        subscriber.cancel();
        first.verifyCancelled();
        subscriber.verifyNoEmissions();
    }

    @Test
    public void testCancelSecondActive() {
        subscriber.subscribe(first.onErrorResume(throwable -> second));
        subscriber.request(1);
        first.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyNoEmissions();
        subscriber.cancel();
        second.verifyCancelled();
    }

    @Test
    public void testDemandAcrossPublishers() {
        subscriber.subscribe(first.onErrorResume(throwable -> second));
        subscriber.request(2);
        first.sendItems(1).onError(DELIBERATE_EXCEPTION);
        subscriber.verifyItems(1).verifyNoEmissions();
        second.sendItems(2).onComplete();
        subscriber.verifySuccess(2);
    }

    @Test
    public void testDuplicateOnError() {
        first = new TestPublisher<>(true);
        first.sendOnSubscribe();
        subscriber.subscribe(first.onErrorResume(throwable -> second));
        subscriber.request(1);
        first.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyNoEmissions();
        second.sendItems(1).onComplete();
        subscriber.verifySuccess(1);
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        DeliberateException ex = new DeliberateException();
        subscriber.subscribe(first.onErrorResume(throwable -> {
            throw ex;
        }));
        subscriber.request(1);
        first.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(ex);
        assertEquals(1, ex.getSuppressed().length);
        assertSame(DELIBERATE_EXCEPTION, ex.getSuppressed()[0]);
    }

    @Test
    public void nullInTerminalCallsOnError() {
        subscriber.subscribe(first.onErrorResume(throwable -> null));
        subscriber.request(1);
        first.onError(DELIBERATE_EXCEPTION);
        subscriber.verifyFailure(NullPointerException.class);
    }
}
