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
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;

abstract class AbstractOpenTelemetryFilter implements HttpExecutionStrategyInfluencer {
    static final OpenTelemetryOptions DEFAULT_OPTIONS = new OpenTelemetryOptions.Builder().build();
    static final String INSTRUMENTATION_SCOPE_NAME = "io.servicetalk";

    @Override
    public final HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    static Single<StreamingHttpResponse> withContext(Single<StreamingHttpResponse> responseSingle, Context context) {
        return doWithContext(responseSingle, context, null);
    }

    static Single<StreamingHttpResponse> withContext(StreamingHttpRequest request,
                                                       Single<StreamingHttpResponse> responseSingle, Context context) {
        return doWithContext(responseSingle, context, request);
    }

    private static Single<StreamingHttpResponse> doWithContext(Single<StreamingHttpResponse> responseSingle,
                                                               Context context,
                                                               @Nullable StreamingHttpRequest request) {
        return new SubscribableSingle<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(SingleSource.Subscriber<? super StreamingHttpResponse> subscriber) {
                try (Scope ignored = context.makeCurrent()) {
                    Single<StreamingHttpResponse> result = responseSingle.map(resp ->
                                    resp.transformMessageBody(body -> {
                                        Publisher<?> publisher = transformBody(body, context);
                                        if (request != null) {
                                            // This should not be race because if request body is already subscribed,
                                            // we don't need this `transformBody`, but if it will be subscribed later
                                            // (auto-draining), then it's not racy to apply a transformation here.
                                            publisher = publisher.beforeFinally(() -> request
                                                    .transformMessageBody(b -> transformBody(b, context)));
                                        }
                                        return publisher;
                                    }));

                    // We also need to make sure the body is cleaned up if we don't get a response at all.
                    if (request != null) {
                        result = result.beforeOnError(ignored2 ->
                                request.transformMessageBody(b -> transformBody(b, context)));
                    }
                    SourceAdapters.toSource(result.shareContextOnSubscribe()).subscribe(subscriber);
                }
            }
        };
    }

    private static <T> Publisher<T> transformBody(Publisher<T> body, Context context) {
        return new SubscribablePublisher<T>() {
            @Override
            protected void handleSubscribe(PublisherSource.Subscriber<? super T> subscriber) {
                try (Scope ignored = context.makeCurrent()) {
                    SourceAdapters.toSource(body.shareContextOnSubscribe()).subscribe(subscriber);
                }
            }
        };
    }
}
