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
package io.servicetalk.http.api;

/**
 * A special type of {@link BlockingStreamingHttpConnection} for the exclusive use of the caller of
 * {@link BlockingStreamingHttpClient#reserveConnection(HttpRequestMetaData)}.
 */
public interface ReservedBlockingStreamingHttpConnection extends BlockingStreamingHttpConnection {
    /**
     * Releases this reserved {@link BlockingStreamingHttpConnection} to be used for subsequent requests.
     * This method must be idempotent, i.e. calling multiple times must not have side-effects.
     *
     * @throws Exception if any exception occurs during releasing.
     */
    void release() throws Exception;

    @Override
    ReservedStreamingHttpConnection asStreamingConnection();

    @Override
    default ReservedHttpConnection asConnection() {
        return asStreamingConnection().asConnection();
    }

    @Override
    default ReservedBlockingHttpConnection asBlockingConnection() {
        return asStreamingConnection().asBlockingConnection();
    }
}
