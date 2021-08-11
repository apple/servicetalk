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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;

import java.nio.charset.Charset;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpDataSourceTransformations.isAlwaysEmpty;
import static java.util.Objects.requireNonNull;

final class DefaultHttpRequest extends AbstractDelegatingHttpRequest
        implements HttpRequest, TrailersTransformer<Object, Buffer> {
    private Buffer payloadBody;
    @Nullable
    private HttpHeaders trailers;

    DefaultHttpRequest(final DefaultStreamingHttpRequest original, final Buffer payloadBody,
                       @Nullable final HttpHeaders trailers) {
        super(original);
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public HttpRequest version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Deprecated
    @Override
    public HttpRequest encoding(final ContentCodec encoding) {
        original.encoding(encoding);
        return this;
    }

    @Override
    public HttpRequest contentEncoding(@Nullable final BufferEncoder encoder) {
        original.contentEncoding(encoder);
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
    public HttpRequest requestTarget(final String requestTarget, final Charset encoding) {
        original.requestTarget(requestTarget, encoding);
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
    public HttpRequest rawQuery(@Nullable final String query) {
        original.rawQuery(query);
        return this;
    }

    @Override
    public HttpRequest query(@Nullable final String query) {
        original.query(query);
        return this;
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
    public Buffer payloadBody() {
        if (payloadBody == EMPTY_BUFFER) {  // default value after aggregation,
            // override with a new empty buffer to allow users expand it with more data:
            payloadBody = original.payloadHolder().allocator().newBuffer(0, false);
            // The correct DefaultPayloadInfo#setEmpty(...) flag will be set in toStreamingRequest()
        }
        return payloadBody;
    }

    @Override
    public <T> T payloadBody(final HttpDeserializer2<T> deserializer) {
        return deserializer.deserialize(headers(), original.payloadHolder().allocator(), payloadBody);
    }

    @Override
    public HttpRequest payloadBody(final Buffer payloadBody) {
        this.payloadBody = requireNonNull(payloadBody);
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
    public <T> HttpRequest payloadBody(final T pojo, final HttpSerializer2<T> serializer) {
        this.payloadBody = serializer.serialize(headers(), pojo, original.payloadHolder().allocator());
        original.payloadBody(from(payloadBody));
        return this;
    }

    @Override
    public HttpHeaders trailers() {
        if (trailers == null) {
            trailers = original.payloadHolder().headersFactory().newTrailers();
            original.transform(this);
        }
        return trailers;
    }

    @Override
    public Object newState() {
        return null;
    }

    @Override
    public Buffer accept(final Object __, final Buffer buffer) {
        return buffer;
    }

    @Override
    public HttpHeaders payloadComplete(final Object __, final HttpHeaders extTrailers) {
        return trailers == null ? extTrailers : trailers;
    }

    @Override
    public HttpHeaders catchPayloadFailure(final Object __, final Throwable cause, final HttpHeaders ___)
            throws Throwable {
        throw cause;
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        final boolean emptyPayloadBody = isAlwaysEmpty(payloadBody);
        @Nullable
        final Publisher<Object> payload;
        if (trailers != null) {
            payload = emptyPayloadBody ? from(trailers) : from(payloadBody, trailers);
        } else {
            payload = emptyPayloadBody ? null : from(payloadBody);
        }
        final DefaultPayloadInfo payloadInfo = new DefaultPayloadInfo(this).setEmpty(emptyPayloadBody)
                .setMayHaveTrailersAndGenericTypeBuffer(trailers != null);
        return new DefaultStreamingHttpRequest(method(), requestTarget(), version(), headers(), encoding(),
                contentEncoding(), original.payloadHolder().allocator(), payload, payloadInfo,
                original.payloadHolder().headersFactory());
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return toStreamingRequest().toBlockingStreamingRequest();
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

        if (!payloadBody.equals(that.payloadBody)) {
            return false;
        }
        return trailers != null ? trailers.equals(that.trailers) : that.trailers == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + payloadBody.hashCode();
        result = 31 * result + (trailers != null ? trailers.hashCode() : 0);
        return result;
    }
}
