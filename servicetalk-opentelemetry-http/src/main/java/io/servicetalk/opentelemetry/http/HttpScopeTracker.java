/*
 * Copyright © 2022-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.AfterFinallyHttpOperator;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import javax.annotation.Nullable;

final class HttpScopeTracker extends AbstractScopeTracker<HttpResponseMetaData> {

    private final Context context;
    private final RequestInfo requestInfo;
    private final Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter;
    @Nullable
    private HttpResponseMetaData responseMetaData;

    private HttpScopeTracker(boolean isClient, Context context, RequestInfo requestInfo,
                             Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter) {
        super(isClient);
        this.context = context;
        this.requestInfo = requestInfo;
        this.instrumenter = instrumenter;
    }

    static HttpScopeTracker client(Context context, RequestInfo requestInfo,
                                   Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter) {
        return new HttpScopeTracker(true, context, requestInfo, instrumenter);
    }

    static HttpScopeTracker server(Context context, RequestInfo requestInfo,
                                   Instrumenter<RequestInfo, HttpResponseMetaData> instrumenter) {
        HttpScopeTracker tracker = new HttpScopeTracker(false, context, requestInfo, instrumenter);
        requestInfo.request().transformMessageBody(body -> body.afterFinally(tracker::requestComplete));
        return tracker;
    }

    @Override
    void finishSpan(@Nullable Throwable error) {
        instrumenter.end(context, requestInfo, responseMetaData, error);
    }

    @Override
    Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle) {
        // Note that we use `discardEventsAfterCancel` to make sure the events that the observer sees are the
        // same that the users see.
        return responseSingle.liftSync(new AfterFinallyHttpOperator(this, true))
                // AfterFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
                // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed before
                // completion of Meta. So in order for downstream operators to get a consistent view of the data
                // path beforeOnSuccess() needs to be applied last.
                .beforeOnSuccess(this::onResponseMeta);
    }

    private void onResponseMeta(final HttpResponseMetaData metaData) {
        this.responseMetaData = metaData;
    }
}
