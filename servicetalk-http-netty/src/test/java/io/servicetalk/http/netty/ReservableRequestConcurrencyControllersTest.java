/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.RequestConcurrencyController;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.netty.ReservableRequestConcurrencyControllers.IgnoreConsumedEvent;

import org.junit.jupiter.api.Test;

import static io.servicetalk.client.api.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.api.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.api.RequestConcurrencyController.Result.RejectedTemporary;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.netty.ReservableRequestConcurrencyControllers.newController;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservableRequestConcurrencyControllersTest {

    private final TestPublisher<ConsumableEvent<Integer>> limitPublisher = new TestPublisher<>();

    @Test
    void maxConcurrencyRequestAtTime() {
        final int maxRequestCount = 10;
        RequestConcurrencyController controller =
                newController(from(new IgnoreConsumedEvent<>(maxRequestCount)), never(), maxRequestCount);
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
    void limitIsAllowedToIncrease() {
        RequestConcurrencyController controller = newController(limitPublisher, never(), 10);
        for (int i = 1; i < 100; ++i) {
            limitPublisher.onNext(new IgnoreConsumedEvent<>(i));
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
    void limitIsAllowedToDecrease() {
        int maxRequestCount = 10;
        RequestConcurrencyController controller = newController(limitPublisher, never(), 10);

        for (int j = 0; j < maxRequestCount; ++j) {
            assertThat(controller.tryRequest(), is(Accepted));
        }
        assertThat(controller.tryRequest(), is(RejectedTemporary));
        limitPublisher.onNext(new IgnoreConsumedEvent<>(0));

        for (int j = 0; j < maxRequestCount; ++j) {
            controller.requestFinished();
        }

        assertThat(controller.tryRequest(), is(RejectedTemporary));
    }

    @Test
    void noMoreRequestsAfterClose() {
        RequestConcurrencyController controller = newController(from(new IgnoreConsumedEvent<>(1)), completed(), 10);
        assertThat(controller.tryRequest(), is(RejectedPermanently));
    }

    @Test
    void defaultValueIsUsed() {
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

    @Test
    void reserveWithNoRequests() throws Exception {
        ReservableRequestConcurrencyController controller =
                newController(from(new IgnoreConsumedEvent<>(10)), never(), 10);
        for (int i = 0; i < 10; ++i) {
            assertTrue(controller.tryReserve());
            assertFalse(controller.tryReserve());

            Completable release = controller.releaseAsync();

            // Test coldness
            assertFalse(controller.tryReserve());

            release.toFuture().get();
        }
    }

    @Test
    void reserveFailsWhenPendingRequest() {
        ReservableRequestConcurrencyController controller =
                newController(from(new IgnoreConsumedEvent<>(10)), never(), 10);
        assertThat(controller.tryRequest(), is(Accepted));
        assertFalse(controller.tryReserve());
    }
}
