/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.zipkin.publisher.reporter;

import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

/**
 * Zipkin data formats for reporting of {@link Span}s.
 */
public enum Codec {
    /**
     * Zipkin V1 JSON format.
     */
    JSON_V1(SpanBytesEncoder.JSON_V1),
    /**
     * Zipkin V2 JSON format.
     */
    JSON_V2(SpanBytesEncoder.JSON_V2),
    /**
     * Zipkin V2 THRIFT format.
     */
    THRIFT(SpanBytesEncoder.THRIFT),
    /**
     * Zipkin V2 protocol buffers V3 format.
     */
    PROTO3(SpanBytesEncoder.PROTO3);

    private final SpanBytesEncoder encoder;

    Codec(SpanBytesEncoder encoder) {
        this.encoder = encoder;
    }

    SpanBytesEncoder spanBytesEncoder() {
        return encoder;
    }
}
