/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionAdapter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.http.utils.LoggingUtils.estimateSize;
import static io.servicetalk.http.utils.LoggingUtils.formatCanonicalAddress;
import static java.lang.System.nanoTime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A {@link StreamingHttpConnection} that logs when interesting events occur during the request/response lifecycle.
 */
public final class ResponseStatusLoggingStreamingHttpConnectionFilter extends
                                                                      StreamingHttpConnectionAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            ResponseStatusLoggingStreamingHttpConnectionFilter.class);
    private final CharSequence name;

    /**
     * Create a new instance.
     * @param name The name to use during logging.
     * @param next The next {@link StreamingHttpConnection} in the filter chain.
     */
    public ResponseStatusLoggingStreamingHttpConnectionFilter(final CharSequence name,
                                                              final StreamingHttpConnection next) {
        super(next);
        this.name = requireNonNull(name);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        return new Single<StreamingHttpResponse>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
                final long startTime = nanoTime();
                final HttpRequestMethod method = request.method();
                final String path = request.path();
                final HttpProtocolVersion version = request.version();
                delegate().request(request).map(resp -> {
                    final int responseCode = resp.status().code();
                    return resp.transformRawPayloadBody(pub ->
                            pub.doAfterSubscriber(() -> new org.reactivestreams.Subscriber<Object>() {
                                private int responseSize;

                                @Override
                                public void onSubscribe(final Subscription s) {
                                }

                                @Override
                                public void onNext(final Object o) {
                                    responseSize += estimateSize(o);
                                }

                                @Override
                                public void onError(final Throwable t) {
                                    LOGGER.debug(
                                        "failed name={} SRC={} DST={} line=\"{} {} {}\" code={} size={} duration={}ms",
                                            name,
                                            formatCanonicalAddress(connectionContext().localAddress()),
                                            formatCanonicalAddress(connectionContext().remoteAddress()),
                                            method, path, version, responseCode, responseSize,
                                            NANOSECONDS.toMillis(nanoTime() - startTime), t);
                                }

                                @Override
                                public void onComplete() {
                                    LOGGER.debug(
                                            "name={} SRC={} DST={} line=\"{} {} {}\" code={} size={} duration={}ms",
                                            name,
                                            formatCanonicalAddress(connectionContext().localAddress()),
                                            formatCanonicalAddress(connectionContext().remoteAddress()),
                                            method, path, version, responseCode, responseSize,
                                            NANOSECONDS.toMillis(nanoTime() - startTime));
                                }
                            }));
                }).doOnError(cause ->
                        LOGGER.debug(
                            "failed name={} SRC={} DST={} line=\"{} {} {}\" duration={}ms",
                            name,
                            formatCanonicalAddress(connectionContext().localAddress()),
                            formatCanonicalAddress(connectionContext().remoteAddress()),
                            method, path, version, NANOSECONDS.toMillis(nanoTime() - startTime), cause)
                ).subscribe(subscriber);
            }
        };
    }
}
