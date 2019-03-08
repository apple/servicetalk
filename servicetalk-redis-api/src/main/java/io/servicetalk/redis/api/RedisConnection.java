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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionContext;

import static java.util.Objects.requireNonNull;

/**
 * Represents a single fixed connection to a Redis server.
 */
public abstract class RedisConnection extends RedisRequester {
    /**
     * Get the {@link ConnectionContext}.
     *
     * @return the {@link ConnectionContext}.
     */
    public abstract ConnectionContext connectionContext();

    /**
     * Returns a {@link Publisher} that gives the current value of the setting as well as subsequent changes to the
     * setting value as long as the {@link Subscriber} has expressed enough demand.
     *
     * @param settingKey Name of the setting to fetch.
     * @param <T> Type of the setting value.
     * @return {@link Publisher} for the setting values.
     */
    public abstract <T> Publisher<T> settingStream(SettingKey<T> settingKey);

    /**
     * A key which identifies a configuration setting for a connection. Setting values may change over time.
     *
     * @param <T> Type of the value of this setting.
     */
    @SuppressWarnings("unused")
    public static final class SettingKey<T> {
        /**
         * Option to define max concurrent requests allowed on a connection.
         */
        public static final SettingKey<Integer> MAX_CONCURRENCY = newKeyWithDebugToString("max-concurrency");

        private final String stringRepresentation;

        private SettingKey(String stringRepresentation) {
            this.stringRepresentation = requireNonNull(stringRepresentation);
        }

        private SettingKey() {
            this.stringRepresentation = super.toString();
        }

        /**
         * Creates a new {@link SettingKey} with the specific {@code name}.
         *
         * @param stringRepresentation of the option. This is only used for debugging purpose and not for key equality.
         * @param <T> Type of the value of the option.
         * @return A new {@link SettingKey}.
         */
        static <T> SettingKey<T> newKeyWithDebugToString(String stringRepresentation) {
            return new SettingKey<>(stringRepresentation);
        }

        /**
         * Creates a new {@link SettingKey}.
         *
         * @param <T> Type of the value of the option.
         * @return A new {@link SettingKey}.
         */
        static <T> SettingKey<T> newKey() {
            return new SettingKey<>();
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }
    }
}
