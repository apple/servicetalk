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

package io.servicetalk.opentelemetry;

import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;

abstract class AbstractOpenTelemetryFilter implements HttpExecutionStrategyInfluencer {
    static final String INSTRUMENTATION_SCOPE_NAME = "io.servicetalk";
    final Tracer tracer;
    final ContextPropagators propagators;

    AbstractOpenTelemetryFilter(final OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
        this.propagators = openTelemetry.getPropagators();
    }

    @Override
    public final HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }
}
