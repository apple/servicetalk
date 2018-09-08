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

import io.servicetalk.concurrent.BlockingIterable;

import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link BlockingStreamingHttpRequest}.
 *
 * @param <I> The type of payload of the request.
 */
final class DefaultBlockingStreamingHttpRequest<I> implements BlockingStreamingHttpRequest<I> {
    private final BlockingIterable<I> payloadIterable;
    private final StreamingHttpRequest<?> httpRequest;

    DefaultBlockingStreamingHttpRequest(final StreamingHttpRequest<I> httpRequest) {
        payloadIterable = httpRequest.getPayloadBody().toIterable();
        this.httpRequest = httpRequest;
    }

    private DefaultBlockingStreamingHttpRequest(final DefaultBlockingStreamingHttpRequest<?> httpRequest,
                                                final BlockingIterable<I> payloadIterable) {
        this.httpRequest = httpRequest.httpRequest;
        this.payloadIterable = requireNonNull(payloadIterable);
    }

    @Nullable
    @Override
    public String getScheme() {
        return httpRequest.getScheme();
    }

    @Nullable
    @Override
    public String getUserInfo() {
        return httpRequest.getUserInfo();
    }

    @Nullable
    @Override
    public String getHost() {
        return httpRequest.getHost();
    }

    @Override
    public int getPort() {
        return httpRequest.getPort();
    }

    @Override
    public BlockingStreamingHttpRequest<I> setRawPath(final String path) {
        httpRequest.setRawPath(path);
        return this;
    }

    @Override
    public BlockingStreamingHttpRequest<I> setPath(final String path) {
        httpRequest.setPath(path);
        return this;
    }

    @Override
    public HttpQuery parseQuery() {
        return httpRequest.parseQuery();
    }

    @Override
    public String getRawQuery() {
        return httpRequest.getRawQuery();
    }

    @Override
    public BlockingStreamingHttpRequest<I> setRawQuery(final String query) {
        httpRequest.setRawQuery(query);
        return this;
    }

    @Nullable
    @Override
    public String getEffectiveHost() {
        return httpRequest.getEffectiveHost();
    }

    @Override
    public int getEffectivePort() {
        return httpRequest.getEffectivePort();
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return httpRequest.getVersion();
    }

    @Override
    public BlockingStreamingHttpRequest<I> setVersion(final HttpProtocolVersion version) {
        httpRequest.setVersion(version);
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return httpRequest.getHeaders();
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return httpRequest.toString(headerFilter);
    }

    @Override
    public HttpRequestMethod getMethod() {
        return httpRequest.getMethod();
    }

    @Override
    public BlockingStreamingHttpRequest<I> setMethod(final HttpRequestMethod method) {
        httpRequest.setMethod(method);
        return this;
    }

    @Override
    public String getRequestTarget() {
        return httpRequest.getRequestTarget();
    }

    @Override
    public BlockingStreamingHttpRequest<I> setRequestTarget(final String requestTarget) {
        httpRequest.setRequestTarget(requestTarget);
        return this;
    }

    @Override
    public String getRawPath() {
        return httpRequest.getRawPath();
    }

    @Override
    public String getPath() {
        return httpRequest.getPath();
    }

    @Override
    public BlockingIterable<I> getPayloadBody() {
        return payloadIterable;
    }

    @Override
    public <R> BlockingStreamingHttpRequest<R> transformPayloadBody(
            final Function<BlockingIterable<I>, BlockingIterable<R>> transformer) {
        return new DefaultBlockingStreamingHttpRequest<>(this, transformer.apply(payloadIterable));
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

        final DefaultBlockingStreamingHttpRequest<?> that = (DefaultBlockingStreamingHttpRequest<?>) o;

        return payloadIterable.equals(that.payloadIterable);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadIterable.hashCode();
    }
}
