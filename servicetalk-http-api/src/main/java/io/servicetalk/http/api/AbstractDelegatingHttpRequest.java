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

import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.transport.api.HostAndPort;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

abstract class AbstractDelegatingHttpRequest implements PayloadInfo, HttpRequestMetaData {

    final DefaultStreamingHttpRequest original;

    AbstractDelegatingHttpRequest(final DefaultStreamingHttpRequest original) {
        this.original = original;
    }

    @Override
    @Nullable
    public String queryParameter(final String key) {
        return original.queryParameter(key);
    }

    @Override
    public Iterable<Map.Entry<String, String>> queryParameters() {
        return original.queryParameters();
    }

    @Override
    public Iterable<String> queryParameters(final String key) {
        return original.queryParameters(key);
    }

    @Override
    public Iterator<String> queryParametersIterator(final String key) {
        return original.queryParametersIterator(key);
    }

    @Override
    public Set<String> queryParametersKeys() {
        return original.queryParametersKeys();
    }

    @Override
    public boolean hasQueryParameter(final String key, final String value) {
        return original.hasQueryParameter(key, value);
    }

    @Override
    public int queryParametersSize() {
        return original.queryParametersSize();
    }

    @Override
    public boolean removeQueryParameters(final String key) {
        return original.removeQueryParameters(key);
    }

    @Override
    public boolean removeQueryParameters(final String key, final String value) {
        return original.removeQueryParameters(key, value);
    }

    @Override
    public HttpProtocolVersion version() {
        return original.version();
    }

    @Override
    public HttpHeaders headers() {
        return original.headers();
    }

    @Deprecated
    @Override
    public ContentCodec encoding() {
        return original.encoding();
    }

    @Override
    public BufferEncoder contentEncoding() {
        return original.contentEncoding();
    }

    @Override
    public String toString() {
        return original.toString();
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return original.toString(headerFilter);
    }

    @Override
    public HttpRequestMethod method() {
        return original.method();
    }

    @Override
    public String requestTarget() {
        return original.requestTarget();
    }

    @Override
    public String requestTarget(Charset encoding) {
        return original.requestTarget(encoding);
    }

    @Override
    @Nullable
    public String scheme() {
        return original.scheme();
    }

    @Override
    @Nullable
    public String userInfo() {
        return original.userInfo();
    }

    @Override
    @Nullable
    public String host() {
        return original.host();
    }

    @Override
    public int port() {
        return original.port();
    }

    @Override
    public String rawPath() {
        return original.rawPath();
    }

    @Override
    public String path() {
        return original.path();
    }

    @Override
    public String rawQuery() {
        return original.rawQuery();
    }

    @Override
    public String query() {
        return original.query();
    }

    @Override
    public boolean hasQueryParameter(final String key) {
        return original.hasQueryParameter(key);
    }

    @Nullable
    @Override
    public HostAndPort effectiveHostAndPort() {
        return original.effectiveHostAndPort();
    }

    @Override
    public boolean isEmpty() {
        return original.isEmpty();
    }

    @Override
    public boolean isSafeToAggregate() {
        return original.isSafeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return original.mayHaveTrailers();
    }

    @Override
    public boolean isGenericTypeBuffer() {
        return original.isGenericTypeBuffer();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AbstractDelegatingHttpRequest that = (AbstractDelegatingHttpRequest) o;

        return original.equals(that.original);
    }

    @Override
    public int hashCode() {
        return original.hashCode();
    }
}
