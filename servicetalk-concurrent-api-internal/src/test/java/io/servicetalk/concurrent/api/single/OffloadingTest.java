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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.function.BiFunction;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OffloadingTest extends AbstractSingleOffloadingTest {

    enum OffloadCase {
        NO_OFFLOAD_SUCCESS(0, "none",
                (p, e) -> p, TerminalOperation.COMPLETE,
                APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, nullValue(), nullValue()),
        NO_OFFLOAD_ERROR(0, "none",
                (p, e) -> p, TerminalOperation.ERROR,
                APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, nullValue(), nullValue()),
        NO_OFFLOAD_CANCEL(0, "none",
                (p, e) -> p, TerminalOperation.CANCEL,
                APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, nullValue(), nullValue(), APP_EXECUTOR, APP_EXECUTOR),
        SUBSCRIBE_ON_SUCCESS(1, "subscribe, request",
                Single::subscribeOn, TerminalOperation.COMPLETE,
                APP_EXECUTOR, APP_EXECUTOR, OFFLOAD_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, nullValue(), nullValue()),
        SUBSCRIBE_ON_ERROR(1, "subscribe, request",
                Single::subscribeOn, TerminalOperation.ERROR,
                APP_EXECUTOR, APP_EXECUTOR, OFFLOAD_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, nullValue(), nullValue()),
        SUBSCRIBE_ON_CANCEL(2, "subscribe, request, cancel",
                Single::subscribeOn, TerminalOperation.CANCEL,
                APP_EXECUTOR, APP_EXECUTOR, OFFLOAD_EXECUTOR, nullValue(), nullValue(), APP_EXECUTOR, OFFLOAD_EXECUTOR),
        PUBLISH_ON_SUCCESS(2, "onSubscribe, onComplete",
                Single::publishOn, TerminalOperation.COMPLETE,
                APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, OFFLOAD_EXECUTOR, nullValue(), nullValue()),
        PUBLISH_ON_ERROR(2, "onSubscribe, onError",
                Single::publishOn, TerminalOperation.ERROR,
                APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, OFFLOAD_EXECUTOR, nullValue(), nullValue()),
        PUBLISH_ON_CANCEL(1, "onSubscribe",
                Single::publishOn, TerminalOperation.CANCEL,
                APP_EXECUTOR, APP_EXECUTOR, APP_EXECUTOR, nullValue(), nullValue(), APP_EXECUTOR, APP_EXECUTOR);

        final int offloadsExpected;
        final String expectedOffloads;
        final BiFunction<Single<String>, Executor, Single<String>> offloadOperator;
        final TerminalOperation terminal;
        final EnumMap<CaptureSlot, Matcher<? super String>> threadNameMatchers = new EnumMap<>(CaptureSlot.class);

        OffloadCase(int offloadsExpected, String expectedOffloads,
                    BiFunction<Single<String>, Executor, Single<String>> offloadOperator,
                    TerminalOperation terminal,
                    Matcher<? super String>... threadNameMatchers) {
            this.offloadsExpected = offloadsExpected;
            this.expectedOffloads = expectedOffloads;
            this.offloadOperator = offloadOperator;
            this.terminal = terminal;
            Iterator<Matcher<? super String>> eachMatcher = Arrays.asList(threadNameMatchers).iterator();
            for (CaptureSlot slot : CaptureSlot.values()) {
                if (!eachMatcher.hasNext()) {
                    break;
                }
                this.threadNameMatchers.put(slot, eachMatcher.next());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(OffloadingTest.OffloadCase.class)
    void testOffloading(OffloadingTest.OffloadCase offloadCase) throws InterruptedException {
        int offloads = testOffloading(offloadCase.offloadOperator, offloadCase.terminal);
        assertThat("Unexpected offloads: " + offloadCase.expectedOffloads,
                offloads, CoreMatchers.is(offloadCase.offloadsExpected));
        offloadCase.threadNameMatchers.forEach(capturedThreads::assertCaptured);
    }
}
