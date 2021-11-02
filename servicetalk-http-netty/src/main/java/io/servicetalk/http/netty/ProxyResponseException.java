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

import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.api.RetryableException;

import java.io.IOException;

/**
 * A proxy response exception, that indicates an unexpected response status from a proxy.
 */
public final class ProxyResponseException extends IOException implements RetryableException {
    private static final long serialVersionUID = -1021287419155443499L;

    private final HttpResponseStatus status;

    ProxyResponseException(final String message, final HttpResponseStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the {@link HttpResponseStatus} that was received.
     *
     * @return the {@link HttpResponseStatus} that was received.
     */
    public HttpResponseStatus status() {
        return status;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + status;
    }
}
