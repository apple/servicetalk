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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;

import javax.annotation.Nullable;

final class GrpcTelemetryStatus {

    private static final String GRPC_STATUS_HEADER_NAME = "grpc-status";

    @Nullable
    private final HttpResponseMetaData responseMetaData;

    @Nullable
    private final HttpHeaders trailers;


    private final long statusCode;

    GrpcTelemetryStatus(@Nullable HttpResponseMetaData responseMetaData,
                        @Nullable HttpHeaders trailers) {
        this.responseMetaData = responseMetaData;
        this.trailers = trailers;
        this.statusCode = parseGrpcStatusCode();
    }

    @Nullable
    HttpResponseMetaData getResponseMetaData() {
        return responseMetaData;
    }

    @Nullable
    HttpHeaders getTrailers() {
        return trailers;
    }

    /**
     * Gets the gRPC status code as a parsed long value.
     *
     * @return gRPC status code as long, or Long.MIN_VALUE if not found or invalid
     */
    long getGrpcStatusCode() {
        return statusCode;
    }

    /**
     * Checks if a valid gRPC status code is available.
     *
     * @return true if a valid gRPC status code is present, false otherwise
     */
    boolean hasGrpcStatusCode() {
        return statusCode != Long.MIN_VALUE;
    }

    /**
     * Parses the gRPC status code from trailers first (per HTTP/2 gRPC spec),
     * then falls back to response headers if not found.
     *
     * @return parsed gRPC status code as long, or Long.MIN_VALUE if not found or invalid
     */
    private long parseGrpcStatusCode() {
        String statusString = null;

        // Check trailers first (gRPC HTTP/2 specification compliance)
        if (trailers != null) {
            CharSequence statusFromTrailers = trailers.get(GRPC_STATUS_HEADER_NAME);
            if (statusFromTrailers != null) {
                statusString = statusFromTrailers.toString();
            }
        }
        // Fallback to response headers
        if (statusString == null && responseMetaData != null) {
            CharSequence statusFromHeaders = responseMetaData.headers().get(GRPC_STATUS_HEADER_NAME);
            if (statusFromHeaders != null) {
                statusString = statusFromHeaders.toString();
            }
        }
        if (statusString != null) {
            try {
                return Long.parseLong(statusString);
            } catch (NumberFormatException nfe) {
                // Invalid status code format
            }
        }

        return Long.MIN_VALUE;
    }
}
