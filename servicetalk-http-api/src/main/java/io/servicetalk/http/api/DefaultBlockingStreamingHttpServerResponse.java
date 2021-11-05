/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

final class DefaultBlockingStreamingHttpServerResponse extends BlockingStreamingHttpServerResponse {
    private static final AtomicIntegerFieldUpdater<DefaultBlockingStreamingHttpServerResponse> metaSentUpdater =
            AtomicIntegerFieldUpdater.newUpdater(DefaultBlockingStreamingHttpServerResponse.class, "metaSent");
    private volatile int metaSent;
    private final Consumer<DefaultHttpResponseMetaData> sendMeta;

    DefaultBlockingStreamingHttpServerResponse(final HttpResponseStatus status,
                                               final HttpProtocolVersion version,
                                               final HttpHeaders headers,
                                               final HttpPayloadWriter<Buffer> payloadWriter,
                                               final BufferAllocator allocator,
                                               final Consumer<DefaultHttpResponseMetaData> sendMeta) {
        super(status, version, headers, payloadWriter, allocator);
        this.sendMeta = sendMeta;
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
    public BlockingStreamingHttpServerResponse addCookie(final HttpCookiePair cookie) {
        checkSent();
        return super.addCookie(cookie);
    }

    @Override
    public BlockingStreamingHttpServerResponse addCookie(final CharSequence name, final CharSequence value) {
        checkSent();
        return super.addCookie(name, value);
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final HttpSetCookie cookie) {
        checkSent();
        return super.addSetCookie(cookie);
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final CharSequence name, final CharSequence value) {
        checkSent();
        return super.addSetCookie(name, value);
    }

    @Override
    public BlockingStreamingHttpServerResponse context(final ContextMap context) {
        checkSent();
        return super.context(context);
    }

    private void checkSent() {
        if (metaSent != 0) {
            throwMetaAlreadySent();
        }
    }

    @Override
    public HttpPayloadWriter<Buffer> sendMetaData() {
        if (!markMetaSent()) {
            throwMetaAlreadySent();
        }
        sendMeta.accept(this);
        return payloadWriter();
    }

    boolean markMetaSent() {
        return metaSentUpdater.compareAndSet(this, 0, 1);
    }

    private static void throwMetaAlreadySent() {
        throw new IllegalStateException("Response meta-data is already sent");
    }
}
