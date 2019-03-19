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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpConnectionBuilder;

/**
 * Factory methods for building {@link HttpConnection} (and other API variations) instances.
 */
public final class HttpConnections {

    private HttpConnections() {
        // No instances
    }

    /**
     * Creates a {@link HttpConnectionBuilder} that can be used for building connections to resolved addresses.
     *
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder
     */
    public static <R> HttpConnectionBuilder<R> forResolvedAddresses() {
        return new DefaultHttpConnectionBuilder<>();
    }
}
