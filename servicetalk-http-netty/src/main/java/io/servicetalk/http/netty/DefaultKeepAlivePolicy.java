/*
 * Copyright © 2020, 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

final class DefaultKeepAlivePolicy implements KeepAlivePolicy {
    private final Duration idleDuration;
    private final Duration ackTimeout;
    private final boolean withoutActiveStreams;

    DefaultKeepAlivePolicy(final Duration idleDuration, final Duration ackTimeout, final boolean withoutActiveStreams) {
        this.idleDuration = requireNonNull(idleDuration);
        this.ackTimeout = requireNonNull(ackTimeout);
        this.withoutActiveStreams = withoutActiveStreams;
    }

    @Override
    public Duration idleDuration() {
        return idleDuration;
    }

    @Override
    public Duration ackTimeout() {
        return ackTimeout;
    }

    @Override
    public boolean withoutActiveStreams() {
        return withoutActiveStreams;
    }

    @Override
    public String toString() {
        return "DefaultKeepAlivePolicy{" +
                "idleDuration=" + idleDuration +
                ", ackTimeout=" + ackTimeout +
                ", withoutActiveStreams=" + withoutActiveStreams +
                '}';
    }
}
