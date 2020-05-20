/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

/**
 * Additional exceptions for <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a> specification.
 */
public final class H1SpecExceptions {

    private final boolean allowPrematureClosureBeforePayloadBody;

    H1SpecExceptions(final boolean allowPrematureClosureBeforePayloadBody) {
        this.allowPrematureClosureBeforePayloadBody = allowPrematureClosureBeforePayloadBody;
    }

    /**
     * Allows interpreting a premature connection closures as the end of HTTP/1.1 messages if a receiver has not started
     * to read the payload body yet.
     *
     * @return {@code true} if a premature connection closures before reading the payload body should be considered as
     * the end of HTTP/1.1 messages
     */
    public boolean allowPrematureClosureBeforePayloadBody() {
        return allowPrematureClosureBeforePayloadBody;
    }

    public static final class Builder {

        private boolean allowPrematureClosureBeforePayloadBody;

        /**
         * Allows interpreting a premature connection closures as the end of HTTP/1.1 messages if a receiver has not
         * started to read the payload body yet.
         *
         * @return {@code this}
         */
        public Builder allowPrematureClosureBeforePayloadBody() {
            this.allowPrematureClosureBeforePayloadBody = true;
            return this;
        }

        /**
         * Builds {@link H1SpecExceptions}.
         *
         * @return a new {@link H1SpecExceptions}
         */
        public H1SpecExceptions build() {
            return new H1SpecExceptions(allowPrematureClosureBeforePayloadBody);
        }
    }
}
