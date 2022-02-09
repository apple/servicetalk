/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.HttpResponseException;

/**
 * An exception that represents <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-6.5.14">
 *     417 Expectation Failed</a> response status code.
 * <p>
 * A response will be automatically converted into this exception type when
 * {@link RetryingHttpRequesterFilter.Builder#retryExpectationFailed(boolean)} is set to {@code true}.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7231#section-5.1.1">Expect</a>
 */
public final class ExpectationFailedException extends HttpResponseException {
    private static final long serialVersionUID = -5608030718662972886L;

    ExpectationFailedException(final String message, final HttpResponseMetaData response) {
        super(message, response);
    }
}
