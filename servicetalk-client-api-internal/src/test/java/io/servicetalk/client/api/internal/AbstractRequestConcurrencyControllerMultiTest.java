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
package io.servicetalk.client.api.internal;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.RejectedTemporary;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public abstract class AbstractRequestConcurrencyControllerMultiTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestPublisher<Integer> limitPublisher = new TestPublisher<>();

    protected abstract RequestConcurrencyController newController(Publisher<Integer> maxSetting,
                                                                  Completable onClose,
                                                                  int init);

    @Test
    public void maxConcurrencyRequestAtTime() {
        final int maxRequestCount = 10;
        RequestConcurrencyController controller = newController(from(maxRequestCount), never(), maxRequestCount);
        for (int i = 0; i < 100; ++i) {
            for (int j = 0; j < maxRequestCount; ++j) {
                assertThat(controller.tryRequest(), is(Accepted));
            }
            assertThat(controller.tryRequest(), is(RejectedTemporary));
            for (int j = 0; j < maxRequestCount; ++j) {
                controller.requestFinished();
            }
        }
    }

    @Test
    public void limitIsAllowedToIncrease() {
        RequestConcurrencyController controller = newController(limitPublisher, never(), 10);
        for (int i = 1; i < 100; ++i) {
            limitPublisher.onNext(i);
            for (int j = 0; j < i; ++j) {
                assertThat(controller.tryRequest(), is(Accepted));
            }
            assertThat(controller.tryRequest(), is(RejectedTemporary));
            for (int j = 0; j < i; ++j) {
                controller.requestFinished();
            }
        }
    }

    @Test
    public void limitIsAllowedToDecrease() {
        int maxRequestCount = 10;
        RequestConcurrencyController controller = newController(limitPublisher, never(), 10);

        for (int j = 0; j < maxRequestCount; ++j) {
            assertThat(controller.tryRequest(), is(Accepted));
        }
        assertThat(controller.tryRequest(), is(RejectedTemporary));
        limitPublisher.onNext(0);

        for (int j = 0; j < maxRequestCount; ++j) {
            controller.requestFinished();
        }

        assertThat(controller.tryRequest(), is(RejectedTemporary));
    }

    @Test
    public void noMoreRequestsAfterClose() {
        RequestConcurrencyController controller = newController(from(1), completed(), 10);
        assertThat(controller.tryRequest(), is(RejectedPermanently));
    }

    @Test
    public void defaultValueIsUsed() {
        final int maxRequestCount = 10;
        RequestConcurrencyController controller = newController(limitPublisher, never(), 10);
        for (int j = 0; j < maxRequestCount; ++j) {
            assertThat(controller.tryRequest(), is(Accepted));
        }
        assertThat(controller.tryRequest(), is(RejectedTemporary));
        for (int j = 0; j < maxRequestCount; ++j) {
            controller.requestFinished();
        }
    }
}
