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
package io.servicetalk.transport.netty.internal;

import java.util.function.IntPredicate;

import static io.servicetalk.concurrent.api.Publisher.never;

final class FlushStrategyAndVerifier {

    private final String name;
    private final FlushStrategy flushStrategy;
    private final IntPredicate expectFlushAtIndex;

    private FlushStrategyAndVerifier(final String name, final FlushStrategy flushStrategy,
                                     final IntPredicate expectFlushAtIndex) {
        this.name = name;
        this.flushStrategy = flushStrategy;
        this.expectFlushAtIndex = expectFlushAtIndex;
    }

    static FlushStrategyAndVerifier flushOnEach() {
        return new FlushStrategyAndVerifier("flush-on-each", FlushStrategy.flushOnEach(),
                index -> (index + 1) % 2 == 0);
    }

    static FlushStrategyAndVerifier flushBeforeEnd(int dataLength) {
        return new FlushStrategyAndVerifier("flush-before-end", FlushStrategy.flushBeforeEnd(),
                index -> index == dataLength);
    }

    static FlushStrategyAndVerifier batchFlush(int batchSize) {
        return new FlushStrategyAndVerifier("batch-flush", FlushStrategy.batchFlush(batchSize, never()),
                index -> (index + 1) % (batchSize + 1) == 0);
    }

    static FlushStrategyAndVerifier flushOnReadComplete() {
        return new FlushStrategyAndVerifier("on-read-complete",
                ReadAwareFlushStrategyHolder.flushOnReadComplete(1), index -> (index + 1) % 2 == 0);
    }

    FlushStrategy getFlushStrategy() {
        return flushStrategy;
    }

    IntPredicate getExpectFlushAtIndex() {
        return expectFlushAtIndex;
    }

    @Override
    public String toString() {
        return name;
    }
}
