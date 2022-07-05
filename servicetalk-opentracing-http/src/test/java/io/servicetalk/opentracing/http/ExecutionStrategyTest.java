/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.opentracing.inmemory.DefaultInMemoryTracer;

import io.opentracing.Tracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.opentracing.asynccontext.AsyncContextInMemoryScopeManager.SCOPE_MANAGER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ExecutionStrategyTest {

    @Nullable
    private static Tracer tracer;

    @BeforeAll
    static void setUp() {
        tracer = new DefaultInMemoryTracer.Builder(SCOPE_MANAGER).build();
    }

    @AfterAll
    static void tearDown() {
        if (tracer != null) {
            tracer.close();
        }
    }

    @Test
    void serviceFilter() {
        assert tracer != null;
        assertThat(new TracingHttpServiceFilter(tracer, "test").requiredOffloads(), is(offloadNone()));
    }

    @Test
    void requesterFilter() {
        assert tracer != null;
        assertThat(new TracingHttpRequesterFilter(tracer, "test").requiredOffloads(), is(offloadNone()));
    }
}
