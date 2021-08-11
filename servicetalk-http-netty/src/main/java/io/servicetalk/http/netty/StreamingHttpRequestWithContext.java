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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.HttpSetCookie;
import io.servicetalk.http.api.HttpStreamingDeserializer;
import io.servicetalk.http.api.HttpStreamingSerializer;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.TrailersTransformer;
import io.servicetalk.http.netty.LoadBalancedStreamingHttpClient.OwnedRunnable;
import io.servicetalk.transport.api.HostAndPort;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

final class StreamingHttpRequestWithContext implements StreamingHttpRequest {

    private final StreamingHttpRequest delegate;
    private final OwnedRunnable runnable;

    StreamingHttpRequestWithContext(final StreamingHttpRequest delegate, final OwnedRunnable runnable) {
        this.delegate = delegate;
        this.runnable = runnable;
    }

    OwnedRunnable runnable() {
        return runnable;
    }

    StreamingHttpRequest unwrap() {
        return delegate;
    }

    @Override
    public HttpProtocolVersion version() {
        return delegate.version();
    }

    @Override
    public HttpHeaders headers() {
        return delegate.headers();
    }

    @Deprecated
    @Nullable
    @Override
    public ContentCodec encoding() {
        return delegate.encoding();
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return delegate.toString(headerFilter);
    }

    @Override
    public HttpRequestMethod method() {
        return delegate.method();
    }

    @Override
    public String requestTarget() {
        return delegate.requestTarget();
    }

    @Override
    public String requestTarget(final Charset encoding) {
        return delegate.requestTarget(encoding);
    }

    @Nullable
    @Override
    public String scheme() {
        return delegate.scheme();
    }

    @Nullable
    @Override
    public String userInfo() {
        return delegate.userInfo();
    }

    @Nullable
    @Override
    public String host() {
        return delegate.host();
    }

    @Override
    public int port() {
        return delegate.port();
    }

    @Override
    public String rawPath() {
        return delegate.rawPath();
    }

    @Override
    public String path() {
        return delegate.path();
    }

    @Nullable
    @Override
    public String rawQuery() {
        return delegate.rawQuery();
    }

    @Nullable
    @Override
    public String query() {
        return delegate.query();
    }

    @Nullable
    @Override
    public String queryParameter(final String key) {
        return delegate.queryParameter(key);
    }

    @Override
    public Iterable<Map.Entry<String, String>> queryParameters() {
        return delegate.queryParameters();
    }

    @Override
    public Iterable<String> queryParameters(final String key) {
        return delegate.queryParameters(key);
    }

    @Override
    public Iterator<String> queryParametersIterator(final String key) {
        return delegate.queryParametersIterator(key);
    }

    @Override
    public Set<String> queryParametersKeys() {
        return delegate.queryParametersKeys();
    }

    @Override
    public boolean hasQueryParameter(final String key) {
        return delegate.hasQueryParameter(key);
    }

    @Override
    public boolean hasQueryParameter(final String key, final String value) {
        return delegate.hasQueryParameter(key, value);
    }

    @Override
    public int queryParametersSize() {
        return delegate.queryParametersSize();
    }

    @Override
    public boolean removeQueryParameters(final String key) {
        return delegate.removeQueryParameters(key);
    }

    @Override
    public boolean removeQueryParameters(final String key, final String value) {
        return delegate.removeQueryParameters(key, value);
    }

    @Nullable
    @Override
    public HostAndPort effectiveHostAndPort() {
        return delegate.effectiveHostAndPort();
    }

    @Nullable
    @Override
    public BufferEncoder contentEncoding() {
        return delegate.contentEncoding();
    }

    @Override
    public Publisher<Buffer> payloadBody() {
        return delegate.payloadBody();
    }

    @Deprecated
    @Override
    public <T> Publisher<T> payloadBody(final HttpDeserializer<T> deserializer) {
        return delegate.payloadBody(deserializer);
    }

    @Override
    public <T> Publisher<T> payloadBody(final HttpStreamingDeserializer<T> deserializer) {
        return delegate.payloadBody(deserializer);
    }

    @Override
    public Publisher<Object> messageBody() {
        return delegate.messageBody();
    }

    @Override
    public StreamingHttpRequest payloadBody(final Publisher<Buffer> payloadBody) {
        delegate.payloadBody(payloadBody);
        return this;
    }

    @Deprecated
    @Override
    public <T> StreamingHttpRequest payloadBody(final Publisher<T> payloadBody, final HttpSerializer<T> serializer) {
        delegate.payloadBody(payloadBody, serializer);
        return this;
    }

    @Override
    public <T> StreamingHttpRequest payloadBody(final Publisher<T> payloadBody,
                                                final HttpStreamingSerializer<T> serializer) {
        delegate.payloadBody(payloadBody, serializer);
        return this;
    }

    @Deprecated
    @Override
    public <T> StreamingHttpRequest transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                         final HttpSerializer<T> serializer) {
        delegate.transformPayloadBody(transformer, serializer);
        return this;
    }

    @Override
    public <T> StreamingHttpRequest transformPayloadBody(final Function<Publisher<Buffer>, Publisher<T>> transformer,
                                                         final HttpStreamingSerializer<T> serializer) {
        delegate.transformPayloadBody(transformer, serializer);
        return this;
    }

    @Deprecated
    @Override
    public <T, R> StreamingHttpRequest transformPayloadBody(final Function<Publisher<T>, Publisher<R>> transformer,
                                                            final HttpDeserializer<T> deserializer,
                                                            final HttpSerializer<R> serializer) {
        delegate.transformPayloadBody(transformer, deserializer, serializer);
        return this;
    }

    @Override
    public <T, R> StreamingHttpRequest transformPayloadBody(final Function<Publisher<T>, Publisher<R>> transformer,
                                                            final HttpStreamingDeserializer<T> deserializer,
                                                            final HttpStreamingSerializer<R> serializer) {
        delegate.transformPayloadBody(transformer, deserializer, serializer);
        return this;
    }

    @Override
    public StreamingHttpRequest transformPayloadBody(final UnaryOperator<Publisher<Buffer>> transformer) {
        delegate.transformPayloadBody(transformer);
        return this;
    }

    @Override
    public StreamingHttpRequest transformMessageBody(final UnaryOperator<Publisher<?>> transformer) {
        delegate.transformMessageBody(transformer);
        return this;
    }

    @Override
    public <T> StreamingHttpRequest transform(final TrailersTransformer<T, Buffer> trailersTransformer) {
        delegate.transform(trailersTransformer);
        return this;
    }

    @Override
    public <T, S> StreamingHttpRequest transform(final TrailersTransformer<T, S> trailersTransformer,
                                                 final HttpStreamingDeserializer<S> deserializer) {
        delegate.transform(trailersTransformer, deserializer);
        return this;
    }

    @Override
    public Single<HttpRequest> toRequest() {
        return delegate.toRequest();
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return delegate.toBlockingStreamingRequest();
    }

    @Override
    public StreamingHttpRequest rawPath(final String path) {
        delegate.rawPath(path);
        return this;
    }

    @Override
    public StreamingHttpRequest path(final String path) {
        delegate.path(path);
        return this;
    }

    @Override
    public StreamingHttpRequest appendPathSegments(final String... segments) {
        delegate.appendPathSegments(segments);
        return this;
    }

    @Override
    public StreamingHttpRequest rawQuery(@Nullable final String query) {
        delegate.rawQuery(query);
        return this;
    }

    @Override
    public StreamingHttpRequest query(@Nullable final String query) {
        delegate.query(query);
        return this;
    }

    @Override
    public StreamingHttpRequest addQueryParameter(final String key, final String value) {
        delegate.addQueryParameter(key, value);
        return this;
    }

    @Override
    public StreamingHttpRequest addQueryParameters(final String key, final Iterable<String> values) {
        delegate.addQueryParameters(key, values);
        return this;
    }

    @Override
    public StreamingHttpRequest addQueryParameters(final String key, final String... values) {
        delegate.addQueryParameters(key, values);
        return this;
    }

    @Override
    public StreamingHttpRequest setQueryParameter(final String key, final String value) {
        delegate.setQueryParameter(key, value);
        return this;
    }

    @Override
    public StreamingHttpRequest setQueryParameters(final String key, final Iterable<String> values) {
        delegate.setQueryParameters(key, values);
        return this;
    }

    @Override
    public StreamingHttpRequest setQueryParameters(final String key, final String... values) {
        delegate.setQueryParameters(key, values);
        return this;
    }

    @Override
    public StreamingHttpRequest version(final HttpProtocolVersion version) {
        delegate.version(version);
        return this;
    }

    @Override
    public StreamingHttpRequest method(final HttpRequestMethod method) {
        delegate.method(method);
        return this;
    }

    @Deprecated
    @Override
    public StreamingHttpRequest encoding(final ContentCodec encoding) {
        delegate.encoding(encoding);
        return this;
    }

    @Override
    public StreamingHttpRequest contentEncoding(@Nullable final BufferEncoder encoder) {
        delegate.contentEncoding(encoder);
        return this;
    }

    @Override
    public StreamingHttpRequest requestTarget(final String requestTarget) {
        delegate.requestTarget(requestTarget);
        return this;
    }

    @Override
    public StreamingHttpRequest requestTarget(final String requestTarget, final Charset encoding) {
        delegate.requestTarget(requestTarget, encoding);
        return this;
    }

    @Override
    public StreamingHttpRequest addHeader(final CharSequence name, final CharSequence value) {
        delegate.addHeader(name, value);
        return this;
    }

    @Override
    public StreamingHttpRequest addHeaders(final HttpHeaders headers) {
        delegate.addHeaders(headers);
        return this;
    }

    @Override
    public StreamingHttpRequest setHeader(final CharSequence name, final CharSequence value) {
        delegate.setHeader(name, value);
        return this;
    }

    @Override
    public StreamingHttpRequest setHeaders(final HttpHeaders headers) {
        delegate.setHeaders(headers);
        return this;
    }

    @Override
    public StreamingHttpRequest addCookie(final HttpCookiePair cookie) {
        delegate.addCookie(cookie);
        return this;
    }

    @Override
    public StreamingHttpRequest addCookie(final CharSequence name, final CharSequence value) {
        delegate.addCookie(name, value);
        return this;
    }

    @Override
    public StreamingHttpRequest addSetCookie(final HttpSetCookie cookie) {
        delegate.addSetCookie(cookie);
        return this;
    }

    @Override
    public StreamingHttpRequest addSetCookie(final CharSequence name, final CharSequence value) {
        delegate.addSetCookie(name, value);
        return this;
    }
}
