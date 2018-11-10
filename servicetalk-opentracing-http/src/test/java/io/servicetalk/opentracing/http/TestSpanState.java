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
package io.servicetalk.opentracing.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

/**
 * For test server to send back the extracted span state in JSON. All public for convenience.
 */
public final class TestSpanState {
    public final String traceId;
    public final String spanId;
    @Nullable
    public final String parentSpanId;
    public final boolean sampled;
    public final boolean error;

    @JsonCreator
    public TestSpanState(@JsonProperty("traceId") String traceId,
                         @JsonProperty("spanId") String spanId,
                         @JsonProperty("parentSpanId") @Nullable String parentSpanId,
                         @JsonProperty("sampled") boolean sampled,
                         @JsonProperty("error") boolean error) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sampled = sampled;
        this.error = error;
    }

    @Override
    public String toString() {
        return "TestSpanState{" +
            "traceId='" + traceId + '\'' +
            ", spanId='" + spanId + '\'' +
            ", parentSpanId='" + parentSpanId + '\'' +
            ", sampled=" + sampled +
            ", error=" + error +
            '}';
    }
}
