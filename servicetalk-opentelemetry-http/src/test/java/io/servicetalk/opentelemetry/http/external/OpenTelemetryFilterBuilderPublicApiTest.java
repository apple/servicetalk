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
package io.servicetalk.opentelemetry.http.external;

import io.servicetalk.opentelemetry.http.OpenTelemetryHttpRequesterFilter;
import io.servicetalk.opentelemetry.http.OpenTelemetryHttpServiceFilter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Lives outside of the package of the filters on purpose: it only compiles if the builder methods are part of the
 * public API, which tests in the same package cannot verify.
 */
class OpenTelemetryFilterBuilderPublicApiTest {

    @Test
    void serviceFilterBuilderConfiguresOpenTelemetry() {
        OpenTelemetryHttpServiceFilter filter = assertDoesNotThrow(() ->
                new OpenTelemetryHttpServiceFilter.Builder()
                        .openTelemetry(OpenTelemetry.noop())
                        .build());
        assertThat(filter, is(notNullValue()));
        assertThat(GlobalOpenTelemetry.isSet(), is(false));
    }

    @Test
    void requesterFilterBuilderConfiguresOpenTelemetry() {
        OpenTelemetryHttpRequesterFilter filter = assertDoesNotThrow(() ->
                new OpenTelemetryHttpRequesterFilter.Builder()
                        .openTelemetry(OpenTelemetry.noop())
                        .componentName("test")
                        .build());
        assertThat(filter, is(notNullValue()));
        assertThat(GlobalOpenTelemetry.isSet(), is(false));
    }
}
