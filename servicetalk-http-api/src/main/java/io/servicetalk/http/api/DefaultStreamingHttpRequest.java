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

import io.servicetalk.concurrent.api.Publisher;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link StreamingHttpRequest}.
 *
 * @param <I> The type of payload of the request.
 */
final class DefaultStreamingHttpRequest<I> extends DefaultHttpRequestMetaData implements StreamingHttpRequest<I> {

    private final Publisher<I> payloadBody;

    /**
     * Create a new instance.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param payloadBody a {@link Publisher} of the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     */
    DefaultStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
                                final Publisher<I> payloadBody, final HttpHeaders headers) {
        super(method, requestTarget, version, headers);
        this.payloadBody = requireNonNull(payloadBody);
    }

    private DefaultStreamingHttpRequest(final DefaultStreamingHttpRequest<?> request, final Publisher<I> payloadBody) {
        super(request);
        this.payloadBody = requireNonNull(payloadBody);
    }

    @Override
    public DefaultStreamingHttpRequest<I> setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public DefaultStreamingHttpRequest<I> setMethod(final HttpRequestMethod method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public DefaultStreamingHttpRequest<I> setRequestTarget(final String requestTarget) {
        super.setRequestTarget(requestTarget);
        return this;
    }

    @Override
    public DefaultStreamingHttpRequest<I> setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public DefaultStreamingHttpRequest<I> setRawPath(final String path) {
        super.setRawPath(path);
        return this;
    }

    @Override
    public DefaultStreamingHttpRequest<I> setRawQuery(final String query) {
        super.setRawQuery(query);
        return this;
    }

    @Override
    public Publisher<I> getPayloadBody() {
        return payloadBody;
    }

    @Override
    public <R> StreamingHttpRequest<R> transformPayloadBody(final Function<Publisher<I>, Publisher<R>> transformer) {
        return new DefaultStreamingHttpRequest<>(this, transformer.apply(payloadBody));
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

        final DefaultStreamingHttpRequest<?> that = (DefaultStreamingHttpRequest<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
