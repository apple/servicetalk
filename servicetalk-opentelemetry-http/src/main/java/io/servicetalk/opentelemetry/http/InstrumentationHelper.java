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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import java.util.function.Function;

abstract class InstrumentationHelper {

    private final Instrumenter<RequestInfo, ?> instrumenter;

    InstrumentationHelper(Instrumenter<RequestInfo, ?> instrumenter) {
        this.instrumenter = instrumenter;
    }

    final boolean shouldStart(Context parentContext, RequestInfo requestInfo) {
        return instrumenter.shouldStart(parentContext, requestInfo);
    }

    abstract Single<StreamingHttpResponse> trackRequest(
            Function<StreamingHttpRequest, Single<StreamingHttpResponse>> requestHandler,
            RequestInfo requestInfo,
            Context parentContext);
}
