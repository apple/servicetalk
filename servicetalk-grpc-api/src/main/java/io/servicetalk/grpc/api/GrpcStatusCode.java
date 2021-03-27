/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Integer.parseInt;
import static java.util.Collections.unmodifiableMap;

/**
 * Standard gRPC status codes.
 *
 * @see <a href="https://github.com/grpc/grpc/blob/master/doc/statuscodes.md">Official gRPC status codes</a>
 */
public enum GrpcStatusCode {
    /** Successful. */
    OK(0),
    /** Cancelled (typically by caller). */
    CANCELLED(1),
    /** Unknown error. */
    UNKNOWN(2),
    /** Client specified an invalid argument. */
    INVALID_ARGUMENT(3),
    /** Deadline expired. */
    DEADLINE_EXCEEDED(4),
    /** Some requested entity not found. */
    NOT_FOUND(5),
    /** Some entity that we attempted to create already exists. */
    ALREADY_EXISTS(6),
    /** Permission denied for a particular client. Different from {@link #UNAUTHENTICATED}. */
    PERMISSION_DENIED(7),
    /** Resource exhausted. */
    RESOURCE_EXHAUSTED(8),
    /** The action cannot be executed on the current system state. Client should not retry.. */
    FAILED_PRECONDITION(9),
    /** Aborted, typically due to a concurrency issue (think CAS). Client may retry the whole sequence.. */
    ABORTED(10),
    /** Used for range errors. */
    OUT_OF_RANGE(11),
    /** Unimplemented action. */
    UNIMPLEMENTED(12),
    /** Internal invariant violated. */
    INTERNAL(13),
    /** Service unavailable, similar to 503, client may retry. */
    UNAVAILABLE(14),
    /** Data corruption. */
    DATA_LOSS(15),
    /** Cannot authenticate the client. */
    UNAUTHENTICATED(16);

    private static final Map<Integer, GrpcStatusCode> INT_TO_STATUS_CODE_MAP;

    static {
        final Map<Integer, GrpcStatusCode> intToStatusCodeMap = new HashMap<>(GrpcStatusCode.values().length);
        for (GrpcStatusCode code : GrpcStatusCode.values()) {
            GrpcStatusCode replaced = intToStatusCodeMap.put(code.value(), code);
            if (replaced != null) {
                throw new IllegalStateException(String.format("GrpcStatusCode value %d used by both %s and %s",
                        code.value(), replaced, code));
            }
        }
        INT_TO_STATUS_CODE_MAP = unmodifiableMap(intToStatusCodeMap);
    }

    private final int value;

    GrpcStatusCode(int value) {
        this.value = value;
    }

    /**
     * Obtains the status code given a code value.
     *
     * @param codeValue code value.
     * @return status code associated with the code value, or {@link #UNKNOWN}.
     */
    public static GrpcStatusCode fromCodeValue(CharSequence codeValue) {
        try {
            return fromCodeValue(parseInt(codeValue.toString()));
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }

    /**
     * Obtains the status code given an integer code value.
     *
     * @param codeValue integer code value.
     * @return status code associated with the code value, or {@link #UNKNOWN}.
     */
    public static GrpcStatusCode fromCodeValue(int codeValue) {
        return INT_TO_STATUS_CODE_MAP.getOrDefault(codeValue, UNKNOWN);
    }

    /**
     * Returns the integer code value.
     *
     * @return the integer code value.
     */
    public int value() {
        return value;
    }

    /**
     * Returns a standard {@link GrpcStatus} with this status code.
     *
     * @return a standard {@link GrpcStatus} with this status code.
     */
    public GrpcStatus status() {
        return GrpcStatus.CACHED_INSTANCES.get(value);
    }
}
