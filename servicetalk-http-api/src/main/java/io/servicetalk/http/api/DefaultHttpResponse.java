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

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;

final class DefaultHttpResponse implements HttpResponse, PayloadInfo {

    private final DefaultStreamingHttpResponse original;
    private Buffer payloadBody;
    @Nullable
    private HttpHeaders trailers;

    DefaultHttpResponse(final DefaultStreamingHttpResponse original, final Buffer payloadBody,
                        @Nullable final HttpHeaders trailers) {
        this.original = original;
        this.payloadBody = payloadBody;
        this.trailers = trailers;
    }

    @Override
    public HttpProtocolVersion version() {
        return original.version();
    }

    @Override
    public HttpResponse version(final HttpProtocolVersion version) {
        original.version(version);
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return original.headers();
    }

    @Override
    public HttpResponseStatus status() {
        return original.status();
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
        this.payloadBody = payloadBody;
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
        return original;
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return original.toBlockingStreamingResponse();
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
    public boolean safeToAggregate() {
        return false;
    }

    @Override
    public boolean mayHaveTrailers() {
        return false;
    }

    @Override
    public boolean onlyEmitsBuffer() {
        return false;
    }
}
