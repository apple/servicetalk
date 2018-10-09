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

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for {@link HttpMetaData}.
 */
abstract class AbstractHttpMetaData implements HttpMetaData {
    private HttpProtocolVersion version;
    private final HttpHeaders headers;

    AbstractHttpMetaData(final HttpProtocolVersion version, final HttpHeaders headers) {
        this.version = requireNonNull(version);
        this.headers = requireNonNull(headers);
    }

    AbstractHttpMetaData(final AbstractHttpMetaData metaData) {
        this(metaData.version, metaData.headers);
    }

    @Override
    public final HttpProtocolVersion version() {
        return version;
    }

    @Override
    public HttpMetaData version(final HttpProtocolVersion version) {
        this.version = requireNonNull(version);
        return this;
    }

    @Override
    public final HttpHeaders headers() {
        return headers;
    }

    @Override
    public HttpMetaData addHeader(final CharSequence name, final CharSequence value) {
        headers().add(name, value);
        return this;
    }

    @Override
    public HttpMetaData addHeaders(final HttpHeaders headers) {
        headers().add(headers);
        return this;
    }

    @Override
    public HttpMetaData setHeader(final CharSequence name, final CharSequence value) {
        headers().set(name, value);
        return this;
    }

    @Override
    public HttpMetaData setHeaders(final HttpHeaders headers) {
        headers().set(headers);
        return this;
    }

    @Override
    public HttpMetaData addCookie(final HttpCookie cookie) {
        headers().addCookie(cookie);
        return this;
    }

    @Override
    public HttpMetaData addCookie(final CharSequence name, final CharSequence value) {
        headers().addCookie(name, value);
        return this;
    }

    @Override
    public HttpMetaData addSetCookie(final HttpCookie cookie) {
        headers().addSetCookie(cookie);
        return this;
    }

    @Override
    public HttpMetaData addSetCookie(final CharSequence name, final CharSequence value) {
        headers().addSetCookie(name, value);
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AbstractHttpMetaData that = (AbstractHttpMetaData) o;

        return version.equals(that.version) && headers.equals(that.headers);
    }

    @Override
    public int hashCode() {
        return 31 * version.hashCode() + headers.hashCode();
    }
}
