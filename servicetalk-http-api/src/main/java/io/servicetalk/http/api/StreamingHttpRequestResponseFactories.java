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

import java.util.concurrent.ExecutionException;

final class StreamingHttpRequestResponseFactories {
    private StreamingHttpRequestResponseFactories() {
        // no instances
    }

    static HttpResponse newResponseBlocking(StreamingHttpResponseFactory responseFactory,
                                            HttpResponseStatus status) {
        try {
            return responseFactory.newResponse(status).toResponse().toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    static HttpRequest newRequestBlocking(StreamingHttpRequestFactory requestFactory,
                                          HttpRequestMethod method, String requestTarget) {
        try {
            return requestFactory.newRequest(method, requestTarget).toRequest().toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
