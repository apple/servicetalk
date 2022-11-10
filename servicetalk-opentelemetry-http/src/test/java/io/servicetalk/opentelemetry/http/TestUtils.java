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
package io.servicetalk.opentelemetry.http;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpSerializerDeserializer;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;

import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;
import static io.servicetalk.http.api.HttpSerializers.jsonSerializer;

public final class TestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static final String[] TRACING_TEST_LOG_LINE_PREFIX = new String[] {
        "filter request path={}",
        "filter response map path={}",
        "filter response transform path={}",
        "filter response onSubscribe path={}",
        "filter response onNext path={}",
        "filter response terminated path={}"};

    static final HttpSerializerDeserializer<TestSpanState> SPAN_STATE_SERIALIZER =
        jsonSerializer(JACKSON.serializerDeserializer(TestSpanState.class));

    private TestUtils() {
    }

    static final class TestTracingClientLoggerFilter implements StreamingHttpClientFilterFactory {
        private final String[] logLinePrefix;

        TestTracingClientLoggerFilter(final String[] logLinePrefix) {
            if (logLinePrefix.length < 6) {
                throw new IllegalArgumentException("logLinePrefix length must be >= 6");
            }
            this.logLinePrefix = logLinePrefix.clone();
        }

        @Override
        public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final StreamingHttpRequest request) {
                    LOGGER.debug(logLinePrefix[0], request.path());
                    return delegate.request(request).map(response -> {
                        LOGGER.debug(logLinePrefix[1], request.path());
                        return response.transformMessageBody(payload -> {
                            LOGGER.debug(logLinePrefix[2], request.path());
                            return payload.beforeSubscriber(() -> new PublisherSource.Subscriber<Object>() {
                                @Override
                                public void onSubscribe(final PublisherSource.Subscription subscription) {
                                    LOGGER.debug(logLinePrefix[3], request.path());
                                }

                                @Override
                                public void onNext(@Nullable final Object o) {
                                    LOGGER.debug(logLinePrefix[4], request.path());
                                }

                                @Override
                                public void onError(final Throwable t) {
                                    LOGGER.debug(logLinePrefix[5], request.path());
                                }

                                @Override
                                public void onComplete() {
                                    LOGGER.debug(logLinePrefix[5], request.path());
                                }
                            });
                        });
                    });
                }
            };
        }
    }

    static final class TestTracingServerLoggerFilter implements StreamingHttpServiceFilterFactory {
        private final String[] logLinePrefix;

        TestTracingServerLoggerFilter(final String[] logLinePrefix) {
            if (logLinePrefix.length < 6) {
                throw new IllegalArgumentException("logLinePrefix length must be >= 6");
            }
            this.logLinePrefix = logLinePrefix.clone();
        }

        @Override
        public StreamingHttpServiceFilter create(final StreamingHttpService service) {
            return new StreamingHttpServiceFilter(service) {
                @Override
                public Single<StreamingHttpResponse> handle(
                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                    final StreamingHttpResponseFactory responseFactory) {
                    LOGGER.debug(logLinePrefix[0], request.path());
                    return delegate().handle(ctx, request, responseFactory).map(response -> {
                        LOGGER.debug(logLinePrefix[1], request.path());
                        return response.transformMessageBody(payload -> {
                            LOGGER.debug(logLinePrefix[2], request.path());
                            return payload.beforeSubscriber(() -> new PublisherSource.Subscriber<Object>() {
                                @Override
                                public void onSubscribe(final PublisherSource.Subscription subscription) {
                                    LOGGER.debug(logLinePrefix[3], request.path());
                                }

                                @Override
                                public void onNext(@Nullable final Object o) {
                                    LOGGER.debug(logLinePrefix[4], request.path());
                                }

                                @Override
                                public void onError(final Throwable t) {
                                    LOGGER.debug(logLinePrefix[5], request.path());
                                }

                                @Override
                                public void onComplete() {
                                    LOGGER.debug(logLinePrefix[5], request.path());
                                }
                            });
                        });
                    });
                }
            };
        }
    }
}
