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

import java.util.function.BiFunction;

import static java.lang.System.lineSeparator;
import static java.util.Objects.requireNonNull;

class DefaultHttpResponseMetaData extends AbstractHttpMetaData implements HttpResponseMetaData {

    private HttpResponseStatus status;

    DefaultHttpResponseMetaData(final HttpResponseStatus status, final HttpProtocolVersion version,
                                final HttpHeaders headers) {
        super(version, headers);
        this.status = requireNonNull(status);
    }

    DefaultHttpResponseMetaData(final DefaultHttpResponseMetaData responseMetaData) {
        super(responseMetaData);
        this.status = responseMetaData.status;
    }

    @Override
    public HttpResponseMetaData version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public HttpResponseMetaData addHeader(final CharSequence name, final CharSequence value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public HttpResponseMetaData addHeaders(final HttpHeaders headers) {
        super.addHeaders(headers);
        return this;
    }

    @Override
    public HttpResponseMetaData setHeader(final CharSequence name, final CharSequence value) {
        super.setHeader(name, value);
        return this;
    }

    @Override
    public HttpResponseMetaData setHeaders(final HttpHeaders headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public HttpResponseMetaData addCookie(final HttpCookie cookie) {
        super.addCookie(cookie);
        return this;
    }

    @Override
    public HttpResponseMetaData addCookie(final CharSequence name, final CharSequence value) {
        super.addCookie(name, value);
        return this;
    }

    @Override
    public HttpResponseMetaData addSetCookie(final HttpCookie cookie) {
        super.addSetCookie(cookie);
        return this;
    }

    @Override
    public HttpResponseMetaData addSetCookie(final CharSequence name, final CharSequence value) {
        super.addSetCookie(name, value);
        return this;
    }

    @Override
    public final HttpResponseStatus status() {
        return status;
    }

    @Override
    public HttpResponseMetaData status(final HttpResponseStatus status) {
        this.status = requireNonNull(status);
        return this;
    }

    @Override
    public final String toString(
            final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return version() + " " + status() + lineSeparator()
                + headers().toString(headerFilter);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultHttpResponseMetaData that = (DefaultHttpResponseMetaData) o;

        return status.equals(that.status);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + status.hashCode();
    }
}
