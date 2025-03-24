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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

abstract class AbstractOpenTelemetryFilter implements HttpExecutionStrategyInfluencer {
    static final OpenTelemetryOptions DEFAULT_OPTIONS = new OpenTelemetryOptions.Builder().build();
    static final String INSTRUMENTATION_SCOPE_NAME = "io.servicetalk";

    @Override
    public final HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    static Single<StreamingHttpResponse> withContext(Single<StreamingHttpResponse> responseSingle, Context context) {
        return new SubscribableSingle<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(SingleSource.Subscriber<? super StreamingHttpResponse> subscriber) {
                try (Scope ignored = context.makeCurrent()) {
                    SourceAdapters.toSource(responseSingle.map(resp ->
                                    resp.transformMessageBody(body -> transformBody(body, context))))
                            .subscribe(subscriber);
                }
            }
        };
    }

    private static <T> Publisher<T> transformBody(Publisher<T> body, Context context) {
        return new SubscribablePublisher<T>() {
            @Override
            protected void handleSubscribe(PublisherSource.Subscriber<? super T> subscriber) {
                try (Scope ignored = context.makeCurrent()) {
                    SourceAdapters.toSource(body).subscribe(subscriber);
                }
            }
        };
    }
}
