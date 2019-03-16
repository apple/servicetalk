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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.BlockingStreamingHttpServiceToStreamingHttpService.BufferHttpPayloadWriter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

final class DefaultBlockingStreamingHttpServerResponse extends BlockingStreamingHttpServerResponse {

    private final BufferHttpPayloadWriter payloadWriter;
    private final BiFunction<HttpResponseMetaData, BufferHttpPayloadWriter, HttpPayloadWriter<Buffer>> sendMeta;
    private final AtomicBoolean metaSent;

    DefaultBlockingStreamingHttpServerResponse(final HttpResponseStatus status,
                                               final HttpProtocolVersion version,
                                               final HttpHeaders headers,
                                               final BufferHttpPayloadWriter payloadWriter,
                                               final BufferAllocator allocator,
                                               final BiFunction<HttpResponseMetaData, BufferHttpPayloadWriter,
                                                       HttpPayloadWriter<Buffer>> sendMeta,
                                               final AtomicBoolean metaSent) {
        super(status, version, headers, payloadWriter, allocator);
        this.payloadWriter = payloadWriter;
        this.sendMeta = sendMeta;
        this.metaSent = metaSent;
    }

    @Override
    public BlockingStreamingHttpServerResponse version(final HttpProtocolVersion version) {
        checkSent();
        return super.version(version);
    }

    @Override
    public BlockingStreamingHttpServerResponse status(final HttpResponseStatus status) {
        checkSent();
        return super.status(status);
    }

    @Override
    public BlockingStreamingHttpServerResponse addHeader(final CharSequence name, final CharSequence value) {
        checkSent();
        return super.addHeader(name, value);
    }

    @Override
    public BlockingStreamingHttpServerResponse addHeaders(final HttpHeaders headers) {
        checkSent();
        return super.addHeaders(headers);
    }

    @Override
    public BlockingStreamingHttpServerResponse setHeader(final CharSequence name, final CharSequence value) {
        checkSent();
        return super.setHeader(name, value);
    }

    @Override
    public BlockingStreamingHttpServerResponse setHeaders(final HttpHeaders headers) {
        checkSent();
        return super.setHeaders(headers);
    }

    @Override
    public BlockingStreamingHttpServerResponse addCookie(final HttpCookie cookie) {
        checkSent();
        return super.addCookie(cookie);
    }

    @Override
    public BlockingStreamingHttpServerResponse addCookie(final CharSequence name, final CharSequence value) {
        checkSent();
        return super.addCookie(name, value);
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final HttpCookie cookie) {
        checkSent();
        return super.addSetCookie(cookie);
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final CharSequence name, final CharSequence value) {
        checkSent();
        return super.addSetCookie(name, value);
    }

    private void checkSent() {
        if (metaSent.get()) {
            throw new IllegalStateException("Response meta-data is already sent");
        }
    }

    @Override
    public HttpPayloadWriter<Buffer> sendMetaData() {
        return sendMeta.apply(this, payloadWriter);
    }
}
