/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.RejectedTemporary;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public abstract class AbstractRequestConcurrencyControllerOnlySingleTest {

    private final TestPublisher<Integer> limitPublisher = new TestPublisher<>();

    protected abstract RequestConcurrencyController newController(Publisher<Integer> maxSetting, Completable onClose);

    @Test
    public void singleRequestAtTime() {
        RequestConcurrencyController controller = newController(from(1), never());
        for (int i = 0; i < 100; ++i) {
            assertThat(controller.tryRequest(), is(Accepted));
            assertThat(controller.tryRequest(), is(RejectedTemporary));
            controller.requestFinished();
        }
    }

    @Test
    public void singleRequestEventIfLimitIsHigher() {
        RequestConcurrencyController controller = newController(limitPublisher, never());
        for (int i = 1; i < 100; ++i) {
            limitPublisher.onNext(i);
            assertThat(controller.tryRequest(), is(Accepted));
            assertThat(controller.tryRequest(), is(RejectedTemporary));
            controller.requestFinished();
        }
    }

    @Test
    public void singleRequestEventIfLimitIsLower() {
        RequestConcurrencyController controller = newController(limitPublisher, never());
        limitPublisher.onNext(0);
        assertThat(controller.tryRequest(), is(RejectedPermanently));

        limitPublisher.onNext(1);
        assertThat(controller.tryRequest(), is(Accepted));
        assertThat(controller.tryRequest(), is(RejectedTemporary));

        limitPublisher.onNext(0);
        controller.requestFinished();

        assertThat(controller.tryRequest(), is(RejectedPermanently));

        limitPublisher.onNext(1);
        assertThat(controller.tryRequest(), is(Accepted));
        assertThat(controller.tryRequest(), is(RejectedTemporary));
    }

    @Test
    public void noMoreRequestsAfterClose() {
        RequestConcurrencyController controller = newController(from(1), completed());
        assertThat(controller.tryRequest(), is(RejectedTemporary));
    }
}
