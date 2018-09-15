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
package io.servicetalk.http.api;

import java.util.Objects;

final class StreamingHttpRequestFactoryToBlockingStreamingHttpRequestFactory implements
                                                                         BlockingStreamingHttpRequestFactory {
    private final StreamingHttpRequestFactory requestFactory;

    StreamingHttpRequestFactoryToBlockingStreamingHttpRequestFactory(StreamingHttpRequestFactory requestFactory) {
        this.requestFactory = Objects.requireNonNull(requestFactory);
    }

    @Override
    public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return requestFactory.newRequest(method, requestTarget).toBlockingStreamingRequest();
    }
}
