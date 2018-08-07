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

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link BlockingHttpResponse}.
 *
 * @param <O> The type of payload of the response.
 */
final class DefaultBlockingHttpResponse<O> implements BlockingHttpResponse<O> {
    private final BlockingIterable<O> payloadIterable;
    private final HttpResponse<?> httpResponse;

    DefaultBlockingHttpResponse(final HttpResponse<O> httpResponse) {
        this.payloadIterable = httpResponse.getPayloadBody().toIterable();
        this.httpResponse = httpResponse;
    }

    private DefaultBlockingHttpResponse(final DefaultBlockingHttpResponse<?> httpResponse,
                                        final BlockingIterable<O> payloadIterable) {
        this.httpResponse = httpResponse.httpResponse;
        this.payloadIterable = requireNonNull(payloadIterable);
    }

    @Override
    public HttpProtocolVersion getVersion() {
        return httpResponse.getVersion();
    }

    @Override
    public BlockingHttpResponse<O> setVersion(final HttpProtocolVersion version) {
        httpResponse.setVersion(version);
        return this;
    }

    @Override
    public HttpHeaders getHeaders() {
        return httpResponse.getHeaders();
    }

    @Override
    public String toString(final BiFunction<? super CharSequence, ? super CharSequence, CharSequence> headerFilter) {
        return httpResponse.toString(headerFilter);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return httpResponse.getStatus();
    }

    @Override
    public BlockingHttpResponse<O> setStatus(final HttpResponseStatus status) {
        httpResponse.setStatus(status);
        return this;
    }

    @Override
    public BlockingIterable<O> getPayloadBody() {
        return payloadIterable;
    }

    @Override
    public <R> BlockingHttpResponse<R> transformPayloadBody(final Function<BlockingIterable<O>,
                                                                           BlockingIterable<R>> transformer) {
        return new DefaultBlockingHttpResponse<>(this, transformer.apply(payloadIterable));
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

        final DefaultBlockingHttpResponse<?> that = (DefaultBlockingHttpResponse<?>) o;

        return payloadIterable.equals(that.payloadIterable);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + payloadIterable.hashCode();
    }
}
