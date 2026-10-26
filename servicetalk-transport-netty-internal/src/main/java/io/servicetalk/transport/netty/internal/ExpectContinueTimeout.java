/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.netty.channel.Channel;

/**
 * How long a client request payload body waits for the continuation signal (e.g. {@code 100 (Continue)}) before it is
 * sent anyway. Shared by all connections of a client, so {@link #onTimeout(Channel)} may be invoked concurrently from
 * several event loops.
 */
public interface ExpectContinueTimeout {

    /**
     * Never times out.
     */
    ExpectContinueTimeout NONE = new ExpectContinueTimeout() {
        @Override
        public long timeoutNanos() {
            return 0L;
        }

        @Override
        public void onTimeout(final Channel channel) {
        }
    };

    /**
     * Time to wait for the continuation signal.
     *
     * @return the timeout in nanoseconds, {@code <= 0} waits indefinitely.
     */
    long timeoutNanos();

    /**
     * Invoked on the event loop of the {@code channel} when {@link #timeoutNanos()} expired, right before the payload
     * body is sent.
     *
     * @param channel the {@link Channel} of the request that timed out.
     */
    void onTimeout(Channel channel);
}
