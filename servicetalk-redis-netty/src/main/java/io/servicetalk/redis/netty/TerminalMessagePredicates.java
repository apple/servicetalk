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
package io.servicetalk.redis.netty;

import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.CompleteRedisData;
import io.servicetalk.redis.api.RedisData.LastBulkStringChunk;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

final class TerminalMessagePredicates {

    static final RedisData.Integer ZERO = RedisData.Integer.newInstance(0L);

    interface TerminalMessagePredicate extends Predicate<RedisData> {
        /**
         * Called whenever a message is received on the stream that this predicate is tracking.
         * This method will not be called concurrently or with {@link #test(Object)}.
         *
         * @param message To track.
         */
        void trackMessage(Object message);
    }

    private static final class StreamingResponsePredicate implements TerminalMessagePredicate {
        private long endCounter = 1;

        @Override
        public boolean test(final RedisData data) {
            return endCounter + endCounterDelta(data) == 0;
        }

        @Override
        public void trackMessage(final Object data) {
            endCounter += endCounterDelta(data);
        }

        private long endCounterDelta(final Object data) {
            if (data instanceof RedisData.ArraySize) {
                final RedisData.ArraySize arraySize = (RedisData.ArraySize) data;
                // -1 because the header is a message itself that counts towards the expected number of messages
                return arraySize.getLongValue() - 1;
            }
            return data instanceof CompleteRedisData || data instanceof LastBulkStringChunk ? -1 : 0;
        }
    }

    private static final StatelessPredicate PUB_SUB_PREDICATE = new StatelessPredicate(ZERO::equals);
    private static final StatelessPredicate MONITOR_PREDICATE = new StatelessPredicate(o -> false);

    private static final class StatelessPredicate implements TerminalMessagePredicate {

        private final Predicate<Object> delegate;

        StatelessPredicate(Predicate<Object> delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public void trackMessage(Object message) {
            // Noop, stateless predicate.
        }

        @Override
        public boolean test(RedisData o) {
            return delegate.test(o);
        }
    }

    private TerminalMessagePredicates() {
        // no instances
    }

    /**
     * Creates a new {@link TerminalMessagePredicate} for the passed {@link Command}.
     *
     * @param command {@link Command} for which a {@link TerminalMessagePredicate} is to be created.
     * @return A new {@link TerminalMessagePredicate}.
     */
    static TerminalMessagePredicate forCommand(final Command command) {
        switch (command) {
            case MONITOR:
                return MONITOR_PREDICATE;
            case SUBSCRIBE:
            case PSUBSCRIBE:
                return PUB_SUB_PREDICATE;
            default:
                return new StreamingResponsePredicate();
        }
    }
}
