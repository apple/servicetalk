/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.service.composition;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpResponseStatus.OK;

/**
 * Example client filter that returns an error if the response status code is not 200 OK.
 */
final class ResponseCheckingClientFilter implements StreamingHttpClientFilterFactory {

    private final String backendName;

    ResponseCheckingClientFilter(final String backendName) {
        this.backendName = backendName;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return delegate.request(request).flatMap(response -> {
                    if (!OK.equals(response.status())) {
                        return failed(new BadResponseStatusException("Bad response status from " + backendName + ": " +
                                response.status()));
                    }
                    return succeeded(response);
                });
            }
        };
    }

    static final class BadResponseStatusException extends RuntimeException {
        private static final long serialVersionUID = 8814491402570517144L;

        private BadResponseStatusException(final String message) {
            super(message);
        }
    }
}
