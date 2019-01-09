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
package io.servicetalk.client.internal;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.Test;

import static io.servicetalk.client.internal.ReservableRequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReservableRequestConcurrencyControllerOnlySingleTest extends
                                                                  AbstractRequestConcurrencyControllerOnlySingleTest {
    @Override
    protected ReservableRequestConcurrencyController newController(final Publisher<Integer> maxSetting,
                                                                   final Completable onClose) {
        return newSingleController(maxSetting, onClose);
    }

    @Test
    public void reserveWithNoRequests() throws Exception {
        ReservableRequestConcurrencyController controller = newController(just(10), never());
        for (int i = 0; i < 10; ++i) {
            assertTrue(controller.tryReserve());
            assertFalse(controller.tryReserve());

            Completable release = controller.releaseAsync();

            // Test coldness
            assertFalse(controller.tryReserve());

            awaitIndefinitely(release);
        }
    }

    @Test
    public void reserveFailsWhenPendingRequest() {
        ReservableRequestConcurrencyController controller = newController(just(10), never());
        assertTrue(controller.tryRequest());
        assertFalse(controller.tryReserve());
    }
}
