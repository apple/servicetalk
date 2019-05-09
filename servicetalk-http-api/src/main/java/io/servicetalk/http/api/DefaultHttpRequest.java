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

import io.servicetalk.buffer.api.Buffer;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;

final class DefaultHttpRequest implements HttpRequest, PayloadInfo {

    private final DefaultStreamingHttpRequest original;
    private Buffer payloadBody;
    @Nullable
    private HttpHeaders trailers;

    DefaultHttpRequest(final DefaultStreamingHttpRequest original, final Buffer payloadBody,
                       @Nullable final HttpHeaders trailers) {
        this.original = original;
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public HttpRequest version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Override
    public HttpRequest method(final HttpRequestMethod method) {
        original.method(method);
        return this;
    }

    @Override
    public HttpRequest requestTarget(final String requestTarget) {
        original.requestTarget(requestTarget);
        return this;
    }

    @Override
    public HttpRequest rawPath(final String path) {
        original.rawPath(path);
        return this;
    }

    @Override
    public HttpRequest path(final String path) {
        original.path(path);
        return this;
    }

    @Override
    public HttpRequest appendPathSegments(final String... segments) {
        original.appendPathSegments(segments);
        return this;
    }

    @Override
    public HttpRequest rawQuery(final String query) {
        original.rawQuery(query);
        return this;
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
    public Iterator<String> queryParameters(final String key) {
        return original.queryParameters(key);
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
    public HttpRequest addQueryParameter(final String key, final String value) {
        original.addQueryParameter(key, value);
        return this;
    }

    @Override
    public HttpRequest addQueryParameters(final String key, final Iterable<String> values) {
        original.addQueryParameters(key, values);
        return this;
    }

    @Override
    public HttpRequest addQueryParameters(final String key, final String... values) {
        original.addQueryParameters(key, values);
        return this;
    }

    @Override
    public HttpRequest setQueryParameter(final String key, final String value) {
        original.setQueryParameter(key, value);
        return this;
    }

    @Override
    public HttpRequest setQueryParameters(final String key, final Iterable<String> values) {
        original.setQueryParameters(key, values);
        return this;
    }

    @Override
    public HttpRequest setQueryParameters(final String key, final String... values) {
        original.setQueryParameters(key, values);
        return this;
    }

    @Override
    public boolean removeQueryParameters(final String key) {
        return original.removeQueryParameters(key);
    }

    @Override
    public boolean removeQueryParameters(final String key, final String value) {
        return original.removeQueryParameters(key, value);
    }

    public void setQueryParams(final Map<String, List<String>> params) {
        original.setQueryParams(params);
    }

    @Override
    public HttpProtocolVersion version() {
        return original.version();
    }

    @Override
    public HttpHeaders headers() {
        return original.headers();
    }

    @Override
    public HttpRequest addHeader(final CharSequence name, final CharSequence value) {
        original.addHeader(name, value);
        return this;
    }

    @Override
    public HttpRequest addHeaders(final HttpHeaders headers) {
        original.addHeaders(headers);
        return this;
    }

    @Override
    public HttpRequest setHeader(final CharSequence name, final CharSequence value) {
        original.setHeader(name, value);
        return this;
    }

    @Override
    public HttpRequest setHeaders(final HttpHeaders headers) {
        original.setHeaders(headers);
        return this;
    }

    @Override
    public HttpRequest addCookie(final HttpCookiePair cookie) {
        original.addCookie(cookie);
        return this;
    }

    @Override
    public HttpRequest addCookie(final CharSequence name, final CharSequence value) {
        original.addCookie(name, value);
        return this;
    }

    @Override
    public HttpRequest addSetCookie(final HttpSetCookie cookie) {
        original.addSetCookie(cookie);
        return this;
    }

    @Override
    public HttpRequest addSetCookie(final CharSequence name, final CharSequence value) {
        original.addSetCookie(name, value);
        return this;
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
    public boolean hasQueryParameter(final String key) {
        return original.hasQueryParameter(key);
    }

    @Override
    @Nullable
    public String effectiveHost() {
        return original.effectiveHost();
    }

    @Override
    public int effectivePort() {
        return original.effectivePort();
    }

    @Override
    public Buffer payloadBody() {
        return payloadBody;
    }

    @Override
    public HttpRequest payloadBody(final Buffer payloadBody) {
        this.payloadBody = payloadBody;
        original.payloadBody(from(payloadBody));
        return this;
    }

    @Override
    public <T> HttpRequest payloadBody(final T pojo, final HttpSerializer<T> serializer) {
        this.payloadBody = serializer.serialize(headers(), pojo, original.payloadHolder().allocator());
        original.payloadBody(from(payloadBody));
        return this;
    }

    @Override
    public HttpHeaders trailers() {
        if (trailers == null) {
            trailers = original.payloadHolder().headersFactory().newTrailers();
            original.transform(() -> null, (buffer, o) -> buffer, (o, httpHeaders) -> trailers);
        }
        return trailers;
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        return original;
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return original.toBlockingStreamingRequest();
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

        final DefaultHttpRequest that = (DefaultHttpRequest) o;

        return original.equals(that.original);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + original.hashCode();
        return result;
    }

    @Override
    public boolean safeToAggregate() {
        return original.safeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return original.mayHaveTrailers();
    }

    @Override
    public boolean onlyEmitsBuffer() {
        return original.onlyEmitsBuffer();
    }
}
