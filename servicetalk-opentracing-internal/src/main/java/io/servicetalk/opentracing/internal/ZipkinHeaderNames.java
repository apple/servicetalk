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
package io.servicetalk.opentracing.internal;

/**
 * See <a href="http://zipkin.io/pages/instrumenting.html">zipkin documentation</a>.
 */
public final class ZipkinHeaderNames {
    // Use lowercase so the same header names can be used for HTTP/2 which requires lower case header names.
    // Header names in HTTP/1 should be compared in a case insensitive manner so this shouldn't impact existing
    // deployments.
    /**
     * Header name for the trace id.
     */
    public static final String TRACE_ID = "x-b3-traceid";
    /**
     * Header name for the span id.
     */
    public static final String SPAN_ID = "x-b3-spanid";
    /**
     * Header name for the parent span id.
     */
    public static final String PARENT_SPAN_ID = "x-b3-parentspanid";
    /**
     * Header name which determines if sampling is requested.
     */
    public static final String SAMPLED = "x-b3-sampled";

    private ZipkinHeaderNames() { } // no instantiation
}
