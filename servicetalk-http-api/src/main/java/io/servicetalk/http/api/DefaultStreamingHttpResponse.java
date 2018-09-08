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
 * Default implementation of {@link StreamingHttpResponse}.
 *
 * @param <O> The type of payload of the response.
 */
final class DefaultStreamingHttpResponse<O> extends DefaultHttpResponseMetaData implements StreamingHttpResponse<O> {

    private final Publisher<O> payloadBody;

    /**
     * Create a new instance.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     */
    DefaultStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version, final HttpHeaders headers,
                                 final Publisher<O> payloadBody) {
        super(status, version, headers);
        this.payloadBody = requireNonNull(payloadBody);
    }

    private DefaultStreamingHttpResponse(final DefaultStreamingHttpResponse<?> responseMetaData, final Publisher<O> payloadBody) {
        super(responseMetaData);
        this.payloadBody = requireNonNull(payloadBody);
    }

    @Override
    public StreamingHttpResponse<O> setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public StreamingHttpResponse<O> setStatus(final HttpResponseStatus status) {
        super.setStatus(status);
        return this;
    }

    @Override
    public Publisher<O> getPayloadBody() {
        return payloadBody;
    }

    @Override
    public <R> StreamingHttpResponse<R> transformPayloadBody(final Function<Publisher<O>, Publisher<R>> transformer) {
        return new DefaultStreamingHttpResponse<>(this, transformer.apply(payloadBody));
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

        final DefaultStreamingHttpResponse<?> that = (DefaultStreamingHttpResponse<?>) o;

        return payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadBody.hashCode();
    }
}
