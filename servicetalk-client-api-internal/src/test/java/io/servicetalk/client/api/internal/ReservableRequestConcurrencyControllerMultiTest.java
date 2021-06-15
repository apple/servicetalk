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

import org.junit.jupiter.api.Test;

import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservableRequestConcurrencyControllerMultiTest extends AbstractRequestConcurrencyControllerMultiTest {
    @Override
    protected ReservableRequestConcurrencyController newController(final Publisher<Integer> maxSetting,
                                                                   final Completable onClosing,
                                                                   final int init) {
        return ReservableRequestConcurrencyControllers.newController(maxSetting.map(IgnoreConsumedEvent::new),
                onClosing, init);
    }

    @Test
    void reserveWithNoRequests() throws Exception {
        ReservableRequestConcurrencyController controller = newController(from(10), never(), 10);
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
        ReservableRequestConcurrencyController controller = newController(from(10), never(), 10);
        assertThat(controller.tryRequest(), is(Accepted));
        assertFalse(controller.tryReserve());
    }
}
