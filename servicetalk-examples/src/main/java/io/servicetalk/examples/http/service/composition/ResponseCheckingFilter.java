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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpResponseStatus.OK;

/**
 * Example filter that returns an error if the response status code is not 200 OK.
 */
class ResponseCheckingFilter implements StreamingHttpClientFilterFactory {

    private final String backendName;

    public ResponseCheckingFilter(final String backendName) {
        this.backendName = backendName;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client,
                                            final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return delegate.request(strategy, request).flatMap(response -> {
                    if (!response.status().equals(OK)) {
                        return failed(new BadResponseStatusException("Bad response status from " + backendName + ": " +
                                response.status()));
                    }
                    return succeeded(response);
                });
            }
        };
    }

    public class BadResponseStatusException extends RuntimeException {
        BadResponseStatusException(final String message) {
            super(message);
        }
    }
}
