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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_APP;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_OFFLOADED_CANCEL;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_OFFLOADED_ON_COMPLETE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_OFFLOADED_ON_ERROR;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_OFFLOADED_ON_NEXT;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_OFFLOADED_ON_SUBSCRIBE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_OFFLOADED_REQUEST;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_OFFLOADED_SUBSCRIBE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_ORIGINAL_CANCEL;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_ORIGINAL_ON_COMPLETE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_ORIGINAL_ON_ERROR;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_ORIGINAL_ON_NEXT;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_ORIGINAL_ON_SUBSCRIBE;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_ORIGINAL_REQUEST;
import static io.servicetalk.concurrent.api.internal.AbstractOffloadingTest.CaptureSlot.IN_ORIGINAL_SUBSCRIBE;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OffloadingTest extends AbstractPublisherOffloadingTest {

    enum OffloadCase {
        NO_OFFLOAD_SUCCESS(0, "none",
                (c, e) -> c, TerminalOperation.COMPLETE,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_NEXT, IN_OFFLOADED_ON_NEXT,
                        IN_ORIGINAL_ON_COMPLETE, IN_OFFLOADED_ON_COMPLETE),
                EnumSet.noneOf(CaptureSlot.class)),
        NO_OFFLOAD_ERROR(0, "none",
                (c, e) -> c, TerminalOperation.ERROR,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_ERROR, IN_OFFLOADED_ON_ERROR),
                EnumSet.noneOf(CaptureSlot.class)),
        NO_OFFLOAD_CANCEL(0, "none",
                (c, e) -> c, TerminalOperation.CANCEL,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_CANCEL, IN_OFFLOADED_CANCEL),
                EnumSet.noneOf(CaptureSlot.class)),
        SUBSCRIBE_ON_SUCCESS(2, "subscribe, request",
                Publisher::subscribeOn, TerminalOperation.COMPLETE,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST,
                        IN_ORIGINAL_ON_NEXT, IN_OFFLOADED_ON_NEXT,
                        IN_ORIGINAL_ON_COMPLETE, IN_OFFLOADED_ON_COMPLETE),
                EnumSet.of(IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_OFFLOADED_REQUEST)),
        SUBSCRIBE_ON_CONDITIONAL_NEVER(0, "none",
                (p, e) -> p.subscribeOn(e, () -> Boolean.FALSE::booleanValue), TerminalOperation.COMPLETE,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_NEXT, IN_OFFLOADED_ON_NEXT,
                        IN_ORIGINAL_ON_COMPLETE, IN_OFFLOADED_ON_COMPLETE),
                EnumSet.noneOf(CaptureSlot.class)),
        SUBSCRIBE_ON_CONDITIONAL_SECOND(1, "request",
                (p, e) -> Publisher.defer(() -> {
                    AtomicInteger countdown = new AtomicInteger(1);
                    return p.subscribeOn(e, () -> () -> countdown.decrementAndGet() < 0).subscribeShareContext();
                }), TerminalOperation.COMPLETE,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_NEXT, IN_OFFLOADED_ON_NEXT,
                        IN_ORIGINAL_ON_COMPLETE, IN_OFFLOADED_ON_COMPLETE),
                EnumSet.of(IN_OFFLOADED_REQUEST)),
        SUBSCRIBE_ON_ERROR(2, "subscribe, request",
                Publisher::subscribeOn, TerminalOperation.ERROR,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST,
                        IN_ORIGINAL_ON_ERROR, IN_OFFLOADED_ON_ERROR),
                EnumSet.of(IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_OFFLOADED_REQUEST)),
        SUBSCRIBE_ON_CANCEL(3, "subscribe, request, cancel",
                Publisher::subscribeOn, TerminalOperation.CANCEL,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST,
                        IN_ORIGINAL_CANCEL),
                EnumSet.of(IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_OFFLOADED_REQUEST, IN_OFFLOADED_CANCEL)),
        PUBLISH_ON_SUCCESS(3, "onSubscribe, onNext, onComplete",
                Publisher::publishOn, TerminalOperation.COMPLETE,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_NEXT,
                        IN_ORIGINAL_ON_COMPLETE),
                EnumSet.of(IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_OFFLOADED_ON_NEXT,
                        IN_OFFLOADED_ON_COMPLETE)),
        PUBLISH_ON_CONDITIONAL_NEVER(0, "none",
                (p, e) -> p.publishOn(e, () -> Boolean.FALSE::booleanValue), TerminalOperation.COMPLETE,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_NEXT, IN_OFFLOADED_ON_NEXT,
                        IN_ORIGINAL_ON_COMPLETE, IN_OFFLOADED_ON_COMPLETE),
                EnumSet.noneOf(CaptureSlot.class)),
        PUBLISH_ON_CONDITIONAL_SECOND(2, "onNext, onComplete",
                (p, e) -> Publisher.defer(() -> {
                    AtomicInteger countdown = new AtomicInteger(1);
                    return p.publishOn(e, () -> () -> countdown.decrementAndGet() < 0).subscribeShareContext();
                }), TerminalOperation.COMPLETE,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE, IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_NEXT,
                        IN_ORIGINAL_ON_COMPLETE),
                EnumSet.of(
                        IN_OFFLOADED_ON_NEXT,
                        IN_OFFLOADED_ON_COMPLETE)),
        PUBLISH_ON_ERROR(2, "onSubscribe, onError",
                Publisher::publishOn, TerminalOperation.ERROR,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_ON_ERROR),
                EnumSet.of(IN_OFFLOADED_ON_SUBSCRIBE,
                        IN_OFFLOADED_ON_ERROR)),
        PUBLISH_ON_CANCEL(1, "onSubscribe",
                Publisher::publishOn, TerminalOperation.CANCEL,
                EnumSet.of(IN_ORIGINAL_SUBSCRIBE, IN_OFFLOADED_SUBSCRIBE,
                        IN_ORIGINAL_ON_SUBSCRIBE,
                        IN_ORIGINAL_REQUEST, IN_OFFLOADED_REQUEST,
                        IN_ORIGINAL_CANCEL, IN_OFFLOADED_CANCEL),
                EnumSet.of(IN_OFFLOADED_ON_SUBSCRIBE));

        final int offloadsExpected;
        final String expectedOffloads;
        final BiFunction<Publisher<String>, Executor, Publisher<String>> offloadOperator;
        final TerminalOperation terminal;
        final EnumMap<CaptureSlot, Matcher<? super String>> threadNameMatchers = new EnumMap<>(CaptureSlot.class);

        OffloadCase(int offloadsExpected, String expectedOffloads,
                    BiFunction<Publisher<String>, Executor, Publisher<String>> offloadOperator,
                    TerminalOperation terminal,
                    Set<CaptureSlot> nonOffloaded, EnumSet<CaptureSlot> offloaded) {
            this.offloadsExpected = offloadsExpected;
            this.expectedOffloads = expectedOffloads;
            this.offloadOperator = offloadOperator;
            this.terminal = terminal;
            EnumSet.allOf(CaptureSlot.class).forEach(slot -> this.threadNameMatchers.put(slot, nullValue()));
            this.threadNameMatchers.put(IN_APP, APP_EXECUTOR);
            nonOffloaded.forEach(slot -> this.threadNameMatchers.put(slot, APP_EXECUTOR));
            offloaded.forEach(slot -> this.threadNameMatchers.put(slot, OFFLOAD_EXECUTOR));
        }
    }

    @ParameterizedTest
    @EnumSource(OffloadCase.class)
    void testOffloading(OffloadCase offloadCase) throws InterruptedException {
        int offloads = testOffloading(offloadCase.offloadOperator, offloadCase.terminal);
        assertThat("Unexpected offloads: " + offloadCase.expectedOffloads,
                offloads, CoreMatchers.is(offloadCase.offloadsExpected));
        offloadCase.threadNameMatchers.forEach((slot, matcher) ->
                capturedThreads.assertCaptured("Match failed " + slot + " : " + capturedThreads, slot, matcher));
    }
}
