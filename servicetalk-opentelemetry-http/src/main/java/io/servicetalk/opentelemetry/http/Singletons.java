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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

final class Singletons {

    static final OpenTelemetryOptions DEFAULT_OPTIONS = new OpenTelemetryOptions.Builder().build();
    static final String INSTRUMENTATION_SCOPE_NAME = "io.servicetalk";

    private Singletons() {
        // no instances
    }

    static <T> Single<T> withContext(Single<T> single, Context context) {
        return new SubscribableSingle<T>() {
            @Override
            protected void handleSubscribe(SingleSource.Subscriber<? super T> subscriber) {
                try (Scope ignored = context.makeCurrent()) {
                    SourceAdapters.toSource(single)
                            .subscribe(subscriber);
                }
            }
        };
    }
}
