/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import java.util.EnumSet;

import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_EVENT;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_RECEIVE_DATA;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_RECEIVE_META;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_SEND;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.toMask;

/**
 * Package private default implementation for {@link HttpExecutionStrategy} to be used across programming model
 * adapters, should not be made public.
 *
 * @see SpecialHttpExecutionStrategy
 * @see HttpExecutionStrategies
 */
enum DefaultHttpExecutionStrategy implements HttpExecutionStrategy {

    OFFLOAD_NONE_STRATEGY(EnumSet.noneOf(HttpExecutionStrategies.HttpOffload.class)),
    OFFLOAD_RECEIVE_META_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_META)),
    OFFLOAD_RECEIVE_DATA_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_DATA)),
    OFFLOAD_RECEIVE_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_META, OFFLOAD_RECEIVE_DATA)),
    OFFLOAD_SEND_STRATEGY(EnumSet.of(OFFLOAD_SEND)),
    OFFLOAD_RECEIVE_META_AND_SEND_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_META, OFFLOAD_SEND)),
    OFFLOAD_RECEIVE_DATA_AND_SEND_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_DATA, OFFLOAD_SEND)),
    OFFLOAD_ALL_REQRESP_STRATEGY(EnumSet.allOf(HttpExecutionStrategies.HttpOffload.class)),

    OFFLOAD_EVENT_STRATEGY(EnumSet.of(OFFLOAD_EVENT)),
    OFFLOAD_RECEIVE_META_EVENT_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_META, OFFLOAD_EVENT)),
    OFFLOAD_RECEIVE_DATA_EVENT_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_DATA, OFFLOAD_EVENT)),
    OFFLOAD_RECEIVE_EVENT_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_META, OFFLOAD_RECEIVE_DATA, OFFLOAD_EVENT)),
    OFFLOAD_SEND_EVENT_STRATEGY(EnumSet.of(OFFLOAD_SEND, OFFLOAD_EVENT)),
    OFFLOAD_RECEIVE_META_AND_SEND_EVENT_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_META, OFFLOAD_SEND, OFFLOAD_EVENT)),
    OFFLOAD_RECEIVE_DATA_AND_SEND_EVENT_STRATEGY(EnumSet.of(OFFLOAD_RECEIVE_DATA, OFFLOAD_SEND, OFFLOAD_EVENT)),
    OFFLOAD_ALL_STRATEGY(EnumSet.allOf(HttpExecutionStrategies.HttpOffload.class));

    private static final DefaultHttpExecutionStrategy[] VALUES = values();

    private final byte offloads;

    DefaultHttpExecutionStrategy(EnumSet<HttpExecutionStrategies.HttpOffload> offloads) {
        this.offloads = toMask(offloads);
    }

    static DefaultHttpExecutionStrategy fromMask(byte mask) {
        if (mask < 0 || mask >= VALUES.length) {
            throw new IllegalArgumentException("Unsupported offload flags mask");
        }

        return VALUES[mask];
    }

    @Override
    public boolean hasOffloads() {
        return offloads != 0;
    }

    @Override
    public boolean isMetadataReceiveOffloaded() {
        return offloaded(OFFLOAD_RECEIVE_META);
    }

    @Override
    public boolean isDataReceiveOffloaded() {
        return offloaded(OFFLOAD_RECEIVE_DATA);
    }

    @Override
    public boolean isSendOffloaded() {
        return offloaded(OFFLOAD_SEND);
    }

    @Override
    public boolean isEventOffloaded() {
        return offloaded(OFFLOAD_EVENT);
    }

    @Override
    public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
        if (this == other) {
            return this;
        }

        if (!(other instanceof DefaultHttpExecutionStrategy)) {
            // For consistency with SpecialHttpExecutionStrategy#merge(HttpExecutionStrategy)
            return other.merge(this);
        }

        // merge the offload flags

        byte otherOffloads = generateOffloadsFlag(other);

        return offloads == (offloads | otherOffloads) ?
                this : fromMask((byte) (offloads | otherOffloads));
    }

    private static byte generateOffloadsFlag(final HttpExecutionStrategy strategy) {
        return strategy instanceof DefaultHttpExecutionStrategy ?
                ((DefaultHttpExecutionStrategy) strategy).offloads :
        (byte) ((strategy.isDataReceiveOffloaded() ? OFFLOAD_RECEIVE_DATA.mask() : 0) |
                (strategy.isMetadataReceiveOffloaded() ? OFFLOAD_RECEIVE_META.mask() : 0) |
                (strategy.isSendOffloaded() ? OFFLOAD_SEND.mask() : 0) |
                (strategy.isEventOffloaded() ? OFFLOAD_EVENT.mask() : 0));
    }

    // Visible for testing
    boolean offloaded(HttpExecutionStrategies.HttpOffload flag) {
        byte mask = flag.mask();
        return (offloads & mask) == mask;
    }
}
