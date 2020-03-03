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
package io.servicetalk.transport.api;

import java.net.SocketOption;

import static java.util.Objects.requireNonNull;

/**
 * {@link SocketOption}s that can be used beside {@link java.net.StandardSocketOptions}.
 */
public final class ServiceTalkSocketOptions {

    /**
     * The connect timeout in milliseconds.
     */
    public static final SocketOption<Integer> CONNECT_TIMEOUT =
            new ServiceTalkSocketOption<>("CONNECT_TIMEOUT", Integer.class);

    /**
     * The threshold after which the the Endpoint is not writable anymore.
     */
    public static final SocketOption<Integer> WRITE_BUFFER_THRESHOLD =
            new ServiceTalkSocketOption<>("WRITE_BUFFER_THRESHOLD", Integer.class);

    /**
     * Allow to idle timeout in milli seconds after which the connection is closed.
     */
    public static final SocketOption<Long> IDLE_TIMEOUT = new ServiceTalkSocketOption<>("IDLE_TIMEOUT", Long.class);

    private ServiceTalkSocketOptions() {
    }

    private static final class ServiceTalkSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;

        ServiceTalkSocketOption(String name, Class<T> type) {
            this.name = requireNonNull(name);
            this.type = requireNonNull(type);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ServiceTalkSocketOption<?> that = (ServiceTalkSocketOption<?>) o;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
