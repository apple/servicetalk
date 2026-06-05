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
package io.servicetalk.grpc.api;

import io.servicetalk.serializer.api.SerializationException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.grpc.api.GrpcStatusCode.CANCELLED;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.api.GrpcStatusException.SERIALIZATION_DESCRIPTION_PREFIX;
import static io.servicetalk.grpc.api.GrpcStatusException.UNKNOWN_DESCRIPTION_PREFIX;
import static io.servicetalk.grpc.api.GrpcStatusException.serializationErrorDescription;
import static io.servicetalk.grpc.api.GrpcStatusException.toGrpcStatus;
import static io.servicetalk.grpc.api.GrpcStatusException.unknownStatusDescription;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

class GrpcStatusExceptionTest {

    private static final String SECRET = "top secret string";

    @Test
    void unmappedExceptionDoesNotLeakDetailsToDescription() {
        GrpcStatus status = toGrpcStatus(new IllegalStateException(SECRET));
        assertThat(status.code(), is(UNKNOWN));
        // The opaque reference must not echo the message or the exception class name to the peer.
        assertThat(status.description(), allOf(
                startsWith(UNKNOWN_DESCRIPTION_PREFIX),
                not(containsString(SECRET)),
                not(containsString("IllegalStateException"))));
        // Shape: "internal error (ref: <uuid>)".
        assertThat(status.description(), matchesPattern(
                "internal error \\(ref: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\)"));
    }

    @Test
    void redactedDescriptionsUseUniqueReferences() {
        Throwable cause = new RuntimeException(SECRET);
        String first = unknownStatusDescription(cause, false);
        String second = unknownStatusDescription(cause, false);
        assertThat(first, not(is(second)));
        assertThat(first, not(containsString(SECRET)));
        assertThat(second, not(containsString(SECRET)));
    }

    @Test
    void exposeExceptionDetailsRestoresVerboseDescription() {
        Throwable cause = new IllegalStateException(SECRET);
        assertThat(unknownStatusDescription(cause, true), is(cause.toString()));
    }

    @Test
    void messageEncodingExceptionMappingUnchanged() {
        GrpcStatus status = toGrpcStatus(new MessageEncodingException("snappy"));
        assertThat(status.code(), is(UNIMPLEMENTED));
        assertThat(status.description(), containsString("snappy"));
    }

    @Test
    void serializationExceptionMessageIsRedacted() {
        GrpcStatus status = toGrpcStatus(new SerializationException("boom"));
        assertThat(status.code(), is(UNKNOWN));
        // The category is conveyed but the serializer's message is not echoed to the peer; an opaque reference is
        // included so the failure can be correlated with the full exception logged server-side.
        assertThat(status.description(), allOf(
                startsWith(SERIALIZATION_DESCRIPTION_PREFIX), not(containsString("boom"))));
        assertThat(status.description(), matchesPattern(
                "Serialization error \\(ref: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\)"));
    }

    @Test
    void exposeExceptionDetailsRestoresSerializationMessage() {
        assertThat(serializationErrorDescription(new SerializationException("boom"), true),
                is("Serialization error: boom"));
    }

    @Test
    void timeoutAndCancellationCarryNoDescription() {
        GrpcStatus timeout = toGrpcStatus(new TimeoutException("deadline"));
        assertThat(timeout.code(), is(DEADLINE_EXCEEDED));
        assertThat(timeout.description(), is(nullValue()));

        GrpcStatus cancelled = toGrpcStatus(new CancellationException("cancelled"));
        assertThat(cancelled.code(), is(CANCELLED));
        assertThat(cancelled.description(), is(nullValue()));
    }

    @Test
    void fromThrowableRedactsUnmappedCauseButPreservesExistingStatus() {
        GrpcStatusException fromUnmapped = GrpcStatusException.fromThrowable(new IllegalStateException(SECRET));
        assertThat(fromUnmapped.status().code(), is(UNKNOWN));
        assertThat(fromUnmapped.status().description(), allOf(
                startsWith(UNKNOWN_DESCRIPTION_PREFIX), not(containsString(SECRET))));

        GrpcStatusException existing = new GrpcStatusException(new GrpcStatus(UNKNOWN, "explicit detail"));
        assertThat(GrpcStatusException.fromThrowable(existing), is(existing));
    }
}
