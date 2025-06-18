package io.servicetalk.opentelemetry.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;

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
