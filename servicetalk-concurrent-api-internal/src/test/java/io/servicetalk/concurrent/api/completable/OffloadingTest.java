/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.APP;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.OFFLOADED_CANCEL;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.OFFLOADED_ON_COMPLETE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.OFFLOADED_ON_ERROR;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.OFFLOADED_ON_SUBSCRIBE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.OFFLOADED_SUBSCRIBE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.ORIGINAL_CANCEL;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.ORIGINAL_ON_COMPLETE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.ORIGINAL_ON_ERROR;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.ORIGINAL_ON_SUBSCRIBE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.ORIGINAL_SUBSCRIBE;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OffloadingTest extends AbstractCompletableOffloadingTest {

    enum OffloadCase {
        NO_OFFLOAD_SUCCESS(0, "none",
                (c, e) -> c, TerminalOperation.COMPLETE,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE,
                        ORIGINAL_ON_COMPLETE, OFFLOADED_ON_COMPLETE),
                EnumSet.noneOf(CaptureSlot.class)),
        NO_OFFLOAD_ERROR(0, "none",
                (c, e) -> c, TerminalOperation.ERROR,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE,
                        ORIGINAL_ON_ERROR, OFFLOADED_ON_ERROR),
                EnumSet.noneOf(CaptureSlot.class)),
        NO_OFFLOAD_CANCEL(0, "none",
                (c, e) -> c, TerminalOperation.CANCEL,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE,
                        ORIGINAL_CANCEL, OFFLOADED_CANCEL),
                EnumSet.noneOf(CaptureSlot.class)),
        SUBSCRIBE_ON_SUCCESS(1, "subscribe",
                Completable::subscribeOn, TerminalOperation.COMPLETE,
                EnumSet.of(ORIGINAL_SUBSCRIBE,
                        ORIGINAL_ON_COMPLETE, OFFLOADED_ON_COMPLETE),
                EnumSet.of(OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE)),
        SUBSCRIBE_ON_CONDITIONAL_NEVER(0, "none",
                (c, e) -> c.subscribeOn(e, Boolean.FALSE::booleanValue), TerminalOperation.COMPLETE,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE,
                        ORIGINAL_ON_COMPLETE, OFFLOADED_ON_COMPLETE),
                EnumSet.noneOf(CaptureSlot.class)),
        SUBSCRIBE_ON_ERROR(1, "subscribe",
                Completable::subscribeOn, TerminalOperation.ERROR,
                EnumSet.of(ORIGINAL_SUBSCRIBE,
                        ORIGINAL_ON_ERROR, OFFLOADED_ON_ERROR),
                EnumSet.of(OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE)),
        SUBSCRIBE_ON_CANCEL(2, "subscribe, cancel",
                Completable::subscribeOn, TerminalOperation.CANCEL,
                EnumSet.of(ORIGINAL_SUBSCRIBE, ORIGINAL_CANCEL),
                EnumSet.of(OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE,
                        OFFLOADED_CANCEL)),
        PUBLISH_ON_SUCCESS(2, "onSubscribe, onComplete",
                Completable::publishOn, TerminalOperation.COMPLETE,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, ORIGINAL_ON_COMPLETE),
                EnumSet.of(OFFLOADED_ON_SUBSCRIBE, OFFLOADED_ON_COMPLETE)),
        PUBLISH_ON_CONDITIONAL_NEVER(0, "none",
                (c, e) -> c.publishOn(e, Boolean.FALSE::booleanValue), TerminalOperation.COMPLETE,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE,
                        ORIGINAL_ON_COMPLETE, OFFLOADED_ON_COMPLETE),
                EnumSet.noneOf(CaptureSlot.class)),
        PUBLISH_ON_CONDITIONAL_SECOND(1, "onComplete",
                (c, e) -> Completable.defer(() -> {
                    AtomicInteger countdown = new AtomicInteger(1);
                    return c.publishOn(e, () -> countdown.decrementAndGet() < 0).subscribeShareContext();
                }), TerminalOperation.COMPLETE,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, OFFLOADED_ON_SUBSCRIBE,
                        ORIGINAL_ON_COMPLETE),
                EnumSet.of(OFFLOADED_ON_COMPLETE)),
        PUBLISH_ON_ERROR(2, "onSubscribe, onError",
                Completable::publishOn, TerminalOperation.ERROR,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE, ORIGINAL_ON_ERROR),
                EnumSet.of(OFFLOADED_ON_SUBSCRIBE, OFFLOADED_ON_ERROR)),
        PUBLISH_ON_CANCEL(1, "onSubscribe",
                Completable::publishOn, TerminalOperation.CANCEL,
                EnumSet.of(ORIGINAL_SUBSCRIBE, OFFLOADED_SUBSCRIBE,
                        ORIGINAL_ON_SUBSCRIBE,
                        ORIGINAL_CANCEL, OFFLOADED_CANCEL),
                EnumSet.of(OFFLOADED_ON_SUBSCRIBE));

        final int offloadsExpected;
        final String expectedOffloads;
        final BiFunction<Completable, Executor, Completable> offloadOperator;
        final TerminalOperation terminal;
        final EnumMap<CaptureSlot, Matcher<? super String>> threadNameMatchers = new EnumMap<>(CaptureSlot.class);

        OffloadCase(int offloadsExpected, String expectedOffloads,
                    BiFunction<Completable, Executor, Completable> offloadOperator,
                    TerminalOperation terminal,
                    EnumSet<CaptureSlot> nonOffloaded, EnumSet<CaptureSlot> offloaded) {
            this.offloadsExpected = offloadsExpected;
            this.expectedOffloads = expectedOffloads;
            this.offloadOperator = offloadOperator;
            this.terminal = terminal;
            EnumSet.allOf(CaptureSlot.class).forEach(slot -> this.threadNameMatchers.put(slot, nullValue()));
            this.threadNameMatchers.put(APP, APP_EXECUTOR);
            assertThat("Overlapping non-offloading and offloading slots",
                    Collections.disjoint(nonOffloaded, offloaded));
            nonOffloaded.forEach(slot -> this.threadNameMatchers.put(slot, APP_EXECUTOR));
            offloaded.forEach(slot -> this.threadNameMatchers.put(slot, OFFLOAD_EXECUTOR));
        }
    }

    @ParameterizedTest
    @EnumSource(OffloadingTest.OffloadCase.class)
    void testOffloading(OffloadCase offloadCase) throws InterruptedException {
        int offloads = testOffloading(offloadCase.offloadOperator, offloadCase.terminal);
        assertThat("Unexpected offloads: " + offloadCase.expectedOffloads,
                offloads, CoreMatchers.is(offloadCase.offloadsExpected));
        offloadCase.threadNameMatchers.forEach((slot, matcher) ->
                capturedThreads.assertCaptured("Match failed " + slot + " : " + capturedThreads,
                        capturedStacks.captured(slot), slot, matcher));
    }
}
