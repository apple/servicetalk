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

import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * A factory for creating {@link StreamingHttpResponse}s.
 */
public interface StreamingHttpResponseFactory {
    /**
     * Create a new {@link StreamingHttpResponse} object.
     * @param status The {@link HttpResponseStatus}.
     * @return a new {@link StreamingHttpResponse} object.
     */
    StreamingHttpResponse newResponse(HttpResponseStatus status);

    /**
     * Create a new {@link HttpResponseStatuses#OK} response.
     * @return a new {@link HttpResponseStatuses#OK} response.
     */
    default StreamingHttpResponse ok() {
        return newResponse(OK);
    }

    /**
     * Create a new {@link HttpResponseStatuses#INTERNAL_SERVER_ERROR} response.
     * @return a new {@link HttpResponseStatuses#INTERNAL_SERVER_ERROR} response.
     */
    default StreamingHttpResponse serverError() {
        return newResponse(INTERNAL_SERVER_ERROR);
    }

    /**
     * Get a {@link StreamingHttpRequestFactory}.
     * @return a {@Link StreamingHttpRequestFactory}.
     */
    StreamingHttpRequestFactory getHttpRequestFactory();
}
