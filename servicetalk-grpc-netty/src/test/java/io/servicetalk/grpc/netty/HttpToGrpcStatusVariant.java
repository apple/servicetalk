/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.grpc.api.GrpcStatusCode;

import static io.servicetalk.grpc.api.GrpcStatusCode.ALREADY_EXISTS;
import static io.servicetalk.grpc.api.GrpcStatusCode.DATA_LOSS;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.api.GrpcStatusCode.FAILED_PRECONDITION;
import static io.servicetalk.grpc.api.GrpcStatusCode.INTERNAL;
import static io.servicetalk.grpc.api.GrpcStatusCode.PERMISSION_DENIED;
import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNAUTHENTICATED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNAVAILABLE;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.FORBIDDEN;
import static io.servicetalk.http.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.servicetalk.http.api.HttpResponseStatus.PRECONDITION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE;
import static io.servicetalk.http.api.HttpResponseStatus.REQUEST_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.fromStatusCode;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Different variants of HTTP responses to validate expected behavior.
 * <p>
 * See <a href="https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md">
 * HTTP to gRPC Status Code Mapping</a> and
 * <a href="https://github.com/grpc/grpc-java/blob/master/core/src/main/java/io/grpc/internal/GrpcUtil.java">
 * io.grpc.internal.GrpcUtil.httpStatusToGrpcCode(int)</a> for expected behavior.
 */
enum HttpToGrpcStatusVariant {
    /**
     * When no grpc-status is present.
     */
    NO_GRPC_STATUS {
        @Override
        GrpcStatusCode statusToReturn() {
            throw new UnsupportedOperationException("this variant does not return status");
        }

        @Override
        void assertStatusCodes(final int httpCode, final int stCode, final int grpcJavaCode) {
            // See https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
            if (fromStatusCode(httpCode) == INFORMATIONAL_1XX) {
                assertInformational(httpCode, stCode, grpcJavaCode);
            } else if (httpCode == BAD_REQUEST.code()) {
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, INTERNAL);
            } else if (httpCode == UNAUTHORIZED.code()) {
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, UNAUTHENTICATED);
            } else if (httpCode == FORBIDDEN.code()) {
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, PERMISSION_DENIED);
            } else if (httpCode == NOT_FOUND.code()) {
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, UNIMPLEMENTED);
            } else if (httpCode == PROXY_AUTHENTICATION_REQUIRED.code()) {
                assertStatusesDiffer(httpCode, stCode, UNAUTHENTICATED, grpcJavaCode, UNKNOWN);
            } else if (httpCode == REQUEST_TIMEOUT.code()) {
                assertStatusesDiffer(httpCode, stCode, DEADLINE_EXCEEDED, grpcJavaCode, UNKNOWN);
            } else if (httpCode == PRECONDITION_FAILED.code() || httpCode == EXPECTATION_FAILED.code()) {
                assertStatusesDiffer(httpCode, stCode, FAILED_PRECONDITION, grpcJavaCode, UNKNOWN);
            } else if (httpCode == TOO_MANY_REQUESTS.code()) {
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, UNAVAILABLE);
            } else if (httpCode == REQUEST_HEADER_FIELDS_TOO_LARGE.code()) {
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, INTERNAL);
            } else if (httpCode == NOT_IMPLEMENTED.code()) {
                assertStatusesDiffer(httpCode, stCode, UNIMPLEMENTED, grpcJavaCode, UNKNOWN);
            } else if (httpCode == BAD_GATEWAY.code() ||
                    httpCode == SERVICE_UNAVAILABLE.code() ||
                    httpCode == GATEWAY_TIMEOUT.code()) {
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, UNAVAILABLE);
            } else {
                // 200 is UNKNOWN because there should be a grpc-status in case of truly OK response.
                assertStatusesMatch(httpCode, stCode, grpcJavaCode, UNKNOWN);
            }
        }
    },

    /**
     * When "grpc-status" is present in HTTP headers (Trailers-Only responses).
     */
    GRPC_STATUS_IN_HEADERS {
        @Override
        GrpcStatusCode statusToReturn() {
            return ALREADY_EXISTS;
        }
    },

    /**
     * When "grpc-status" is present in HTTP trailers (Responses with Trailers).
     */
    GRPC_STATUS_IN_TRAILERS {
        @Override
        GrpcStatusCode statusToReturn() {
            return DATA_LOSS;
        }
    },

    /**
     * When no "content-type: application/grpc" header is present in HTTP response.
     */
    NO_CONTENT_TYPE_HEADER {
        @Override
        GrpcStatusCode statusToReturn() {
            throw new UnsupportedOperationException("this variant does not return status");
        }

        @Override
        void assertStatusCodes(final int httpCode, final int stCode, final int grpcJavaCode) {
            // Same behavior as for NO_GRPC_STATUS
            NO_GRPC_STATUS.assertStatusCodes(httpCode, stCode, grpcJavaCode);
        }
    },

    /**
     * When no "content-type: application/grpc" header is present in HTTP response, even if "grpc-status" is.
     */
    NO_CONTENT_TYPE_HEADER_WITH_GRPC_STATUS {
        @Override
        GrpcStatusCode statusToReturn() {
            return RESOURCE_EXHAUSTED;
        }

        @Override
        void assertStatusCodes(final int httpCode, final int stCode, final int grpcJavaCode) {
            // Same behavior as for NO_GRPC_STATUS
            NO_GRPC_STATUS.assertStatusCodes(httpCode, stCode, grpcJavaCode);
        }
    };

    abstract GrpcStatusCode statusToReturn();

    void assertStatusCodes(int httpCode, int stCode, int grpcJavaCode) {
        if (fromStatusCode(httpCode) == INFORMATIONAL_1XX) {
            assertInformational(httpCode, stCode, grpcJavaCode);
        } else {
            // If grpc-status was provided, it must be used
            assertStatusesMatch(httpCode, stCode, grpcJavaCode, statusToReturn());
        }
    }

    private static void assertInformational(int httpCode, int stCode, int grpcJavaCode) {
        // grpc-java maps 1xx responses to error code INTERNAL, we currently map to UNKNOWN. The test
        // server isn't following the http protocol by returning only a 1xx response and each framework
        // catches this exception differently internally.
        assertStatusesDiffer(httpCode, stCode, UNKNOWN, grpcJavaCode, INTERNAL);
    }

    private static void assertStatusesDiffer(int httpCode, int stCode, GrpcStatusCode expectedStStatus,
                                             int grpcJavaCode, GrpcStatusCode expectedGrpcStatus) {
        assertStatus(httpCode, stCode, expectedStStatus);
        assertStatus(httpCode, grpcJavaCode, expectedGrpcStatus);
    }

    private static void assertStatusesMatch(int httpCode, int stCode, int grpcJavaCode, GrpcStatusCode expectedStatus) {
        assertThat("Mismatch between implementations for h2 response code: " + httpCode,
                assertStatus(httpCode, stCode, expectedStatus),
                is(assertStatus(httpCode, grpcJavaCode, expectedStatus)));
    }

    private static GrpcStatusCode assertStatus(int httpCode, int actualCode, GrpcStatusCode expectedStatus) {
        GrpcStatusCode actualStatus = GrpcStatusCode.fromCodeValue(actualCode);
        assertThat("Mismatch for h2 response code: " + httpCode, actualStatus, is(expectedStatus));
        return actualStatus;
    }
}
