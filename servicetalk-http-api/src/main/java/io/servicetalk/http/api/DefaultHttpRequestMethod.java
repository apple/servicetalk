/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class DefaultHttpRequestMethod implements HttpRequestMethod {

    private final String nameString;
    private final Buffer name;
    private final Properties properties;

    DefaultHttpRequestMethod(final Buffer name, final Properties properties) {
        if (name.readableBytes() == 0) {
            throw new IllegalArgumentException("Method name cannot be empty");
        }
        this.name = name;
        this.properties = requireNonNull(properties);
        this.nameString = name.toString(US_ASCII);
    }

    @Override
    public void writeNameTo(final Buffer buffer) {
        buffer.writeBytes(name, name.readerIndex(), name.readableBytes());
    }

    @Override
    public String name() {
        return nameString;
    }

    @Override
    public Properties properties() {
        return properties;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpRequestMethod)) {
            return false;
        }

        final HttpRequestMethod that = (HttpRequestMethod) o;
        /*
         * - name Buffer is ignored for equals/hashCode because it represents nameString and the relationship is
         *   idempotent
         *
         * - properties is ignored for equals/hashCode because they carry additional information which should not alter
         *   the meaning of the method name
         */
        return nameString.equals(that.name());
    }

    @Override
    public int hashCode() {
        return nameString.hashCode();
    }

    @Override
    public String toString() {
        return nameString;
    }

    static final class DefaultHttpRequestMethodProperties implements Properties {

        private final boolean safe;
        private final boolean idempotent;
        private final boolean cacheable;

        DefaultHttpRequestMethodProperties(final boolean safe, final boolean idempotent, final boolean cacheable) {
            this.safe = safe;
            this.idempotent = idempotent;
            this.cacheable = cacheable;
        }

        @Override
        public boolean isSafe() {
            return safe;
        }

        @Override
        public boolean isIdempotent() {
            return idempotent;
        }

        @Override
        public boolean isCacheable() {
            return cacheable;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Properties)) {
                return false;
            }

            final Properties that = (Properties) o;
            return safe == that.isSafe() && idempotent == that.isIdempotent() && cacheable == that.isCacheable();
        }

        @Override
        public int hashCode() {
            int result = safe ? 1 : 0;
            result = 31 * result + (idempotent ? 1 : 0);
            result = 31 * result + (cacheable ? 1 : 0);
            return result;
        }
    }
}
