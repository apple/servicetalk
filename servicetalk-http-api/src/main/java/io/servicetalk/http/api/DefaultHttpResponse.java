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
import io.servicetalk.encoding.api.ContentCodec;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpDataSourceTransformations.isAlwaysEmpty;
import static java.util.Objects.requireNonNull;

final class DefaultHttpResponse extends AbstractDelegatingHttpResponse
        implements HttpResponse, TrailersTransformer<Object, Buffer> {
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

    @Deprecated
    @Override
    public HttpResponse encoding(final ContentCodec encoding) {
        original.encoding(encoding);
        return this;
    }

    @Override
    public HttpResponse status(final HttpResponseStatus status) {
        original.status(status);
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
    public <T> HttpResponse payloadBody(final T pojo, final HttpSerializer2<T> serializer) {
        this.payloadBody = serializer.serialize(headers(), pojo, original.payloadHolder().allocator());
        original.payloadBody(from(payloadBody));
        return this;
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
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
        return new DefaultStreamingHttpResponse(status(), version(), headers(), original.payloadHolder().allocator(),
                payload, payloadInfo, original.payloadHolder().headersFactory());
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
