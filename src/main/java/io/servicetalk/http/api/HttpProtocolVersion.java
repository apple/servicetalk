/**
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

/**
 * HTTP <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">protocol versioning</a>.
 */
public interface HttpProtocolVersion {
    /**
     * Get the <strong>&lt;major&gt;</strong> portion of the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     * @return the <strong>&lt;major&gt;</strong> portion of the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     */
    int getMajorVersion();

    /**
     * Get the <strong>&lt;minor&gt;</strong> portion of the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     * @return the <strong>&lt;minor&gt;</strong> portion of the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">http protocol version</a>.
     */
    int getMinorVersion();

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP-version</a>.
     * @return the <a href="https://tools.ietf.org/html/rfc7230.html#section-2.6">HTTP-version</a>.
     */
    String getHttpVersion();
}
