/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

/**
 * A static factory class for {@link HttpRequestMetaData} objects.
 * <p>
 * This is typically only used by HTTP decoders.
 */
public final class HttpRequestMetaDataFactory {
    private HttpRequestMetaDataFactory() {
        // no instances
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param headers the {@link HttpHeaders} to use for the request.
     * @return a new {@link HttpRequestMetaData}.
     */
    public static HttpRequestMetaData newRequestMetaData(HttpProtocolVersion version, HttpRequestMethod method,
                                                         String requestTarget, HttpHeaders headers) {
        return new DefaultHttpRequestMetaData(method, requestTarget, version, headers, null);
    }
}
