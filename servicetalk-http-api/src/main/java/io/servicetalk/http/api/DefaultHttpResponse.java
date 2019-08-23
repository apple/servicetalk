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

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static java.util.Objects.requireNonNull;

final class DefaultHttpResponse extends AbstractDelegatingHttpResponse
        implements HttpResponse, TrailersTransformer<Object, Buffer> {
    private static final Object NULL_TRAILER_TRANSFORMER_STATE = new Object();

    private Buffer payloadBody;
    @Nullable
    private HttpHeaders trailers;

    DefaultHttpResponse(final DefaultStreamingHttpResponse original, final Buffer payloadBody,
                        @Nullable final HttpHeaders trailers) {
        super(original);
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public HttpResponse version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Override
    public HttpResponse status(final HttpResponseStatus status) {
        original.status(status);
        return this;
    }

    @Override
    public Buffer payloadBody() {
        return payloadBody;
    }

    @Override
    public HttpResponse payloadBody(final Buffer payloadBody) {
        this.payloadBody = requireNonNull(payloadBody);
        original.payloadBody(from(payloadBody));
        return this;
    }

    @Override
    public <T> HttpResponse payloadBody(final T pojo, final HttpSerializer<T> serializer) {
        this.payloadBody = serializer.serialize(headers(), pojo, original.payloadHolder().allocator());
        original.payloadBody(from(payloadBody));
        return this;
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
        Publisher<Object> payload = trailers != null ? from(payloadBody, trailers) : from(payloadBody);
        return new DefaultStreamingHttpResponse(status(), version(), headers(), original.payloadHolder().allocator(),
                payload, new DefaultPayloadInfo(this), original.payloadHolder().headersFactory());
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return original.toBlockingStreamingResponse();
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
        return NULL_TRAILER_TRANSFORMER_STATE;
    }

    @Override
    public Buffer accept(final Object __, final Buffer buffer) {
        return buffer;
    }

    @Override
    public HttpHeaders payloadComplete(final Object __, final HttpHeaders ___) {
        assert trailers != null;
        return trailers;
    }

    @Override
    public HttpHeaders payloadFailed(final Object __, final Throwable cause, final HttpHeaders ___) {
        assert trailers != null;
        return trailers;
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

        final DefaultHttpResponse that = (DefaultHttpResponse) o;

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
