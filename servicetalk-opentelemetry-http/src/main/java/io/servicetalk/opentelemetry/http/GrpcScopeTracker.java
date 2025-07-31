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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.AfterFinallyHttpOperator;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import javax.annotation.Nullable;

final class GrpcScopeTracker extends AbstractScopeTracker {

    private final Context context;
    private final RequestInfo requestInfo;
    private final Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter;
    @Nullable
    private HttpResponseMetaData responseMetaData;
    @Nullable
    private HttpHeaders trailers;

    private GrpcScopeTracker(boolean isClient, Context context, RequestInfo requestInfo,
                             Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter) {
        super(isClient);
        this.context = context;
        this.requestInfo = requestInfo;
        this.instrumenter = instrumenter;
    }

    static GrpcScopeTracker client(Context context, RequestInfo requestInfo,
                                   Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter) {
        return new GrpcScopeTracker(true, context, requestInfo, instrumenter);
    }

    static GrpcScopeTracker server(Context context, RequestInfo requestInfo,
                                   Instrumenter<RequestInfo, GrpcTelemetryStatus> instrumenter) {
        GrpcScopeTracker tracker = new GrpcScopeTracker(false, context, requestInfo, instrumenter);
        requestInfo.request().transformMessageBody(body -> body.afterFinally(tracker::requestComplete));
        return tracker;
    }

    @Override
    void finishSpan(@Nullable Throwable error) {
        // Create GrpcTelemetryStatus with captured response metadata, trailers, and error
        GrpcTelemetryStatus telemetryStatus = new GrpcTelemetryStatus(responseMetaData, trailers);
        instrumenter.end(context, requestInfo, telemetryStatus, error);
    }

    @Override
    Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle) {
        // Note that we use `discardEventsAfterCancel` to make sure the events that the observer sees are the
        // same that the users see.
        return responseSingle
                // We want to make sure we capture our response and add our trailers listener first.
                .map(this::captureTrailers)
                .liftSync(new AfterFinallyHttpOperator(this, true));
    }

    private StreamingHttpResponse captureTrailers(StreamingHttpResponse response) {
        this.responseMetaData = response;
        return response.transformMessageBody(body -> body.beforeOnNext(this::onResponseMessage));
    }

    private void onResponseMessage(Object msg) {
        // We look for trailers which are simply an instance of HttpHeaders.
        if (msg instanceof HttpHeaders) {
            trailers = (HttpHeaders) msg;
        }
    }
}
