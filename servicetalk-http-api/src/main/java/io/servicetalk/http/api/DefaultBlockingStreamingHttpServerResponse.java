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
import io.servicetalk.encoding.api.ContentCodec;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

final class DefaultBlockingStreamingHttpServerResponse extends DefaultHttpResponseMetaData
        implements BlockingStreamingHttpServerResponse {

    private static final AtomicIntegerFieldUpdater<DefaultBlockingStreamingHttpServerResponse> metaSentUpdater =
            AtomicIntegerFieldUpdater.newUpdater(DefaultBlockingStreamingHttpServerResponse.class, "metaSent");
    private volatile int metaSent;
    private final Consumer<DefaultHttpResponseMetaData> sendMeta;
    private final HttpPayloadWriter<Buffer> payloadWriter;
    private final BufferAllocator allocator;

    DefaultBlockingStreamingHttpServerResponse(final HttpResponseStatus status,
                                               final HttpProtocolVersion version,
                                               final HttpHeaders headers,
                                               final HttpPayloadWriter<Buffer> payloadWriter,
                                               final BufferAllocator allocator,
                                               final Consumer<DefaultHttpResponseMetaData> sendMeta) {
        super(status, version, headers, null);
        this.payloadWriter = requireNonNull(payloadWriter);
        this.allocator = requireNonNull(allocator);
        this.sendMeta = sendMeta;
    }

    @Override
    public BlockingStreamingHttpServerResponse version(final HttpProtocolVersion version) {
        checkSent();
        super.version(version);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse status(final HttpResponseStatus status) {
        checkSent();
        super.status(status);
        return this;
    }

    @Deprecated
    @Override
    public BlockingStreamingHttpServerResponse encoding(final ContentCodec encoding) {
        checkSent();
        super.encoding(encoding);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addHeader(final CharSequence name, final CharSequence value) {
        checkSent();
        super.addHeader(name, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addHeaders(final HttpHeaders headers) {
        checkSent();
        super.addHeaders(headers);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse setHeader(final CharSequence name, final CharSequence value) {
        checkSent();
        super.setHeader(name, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse setHeaders(final HttpHeaders headers) {
        checkSent();
        super.setHeaders(headers);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addCookie(final HttpCookiePair cookie) {
        checkSent();
        super.addCookie(cookie);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addCookie(final CharSequence name, final CharSequence value) {
        checkSent();
        super.addCookie(name, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final HttpSetCookie cookie) {
        checkSent();
        super.addSetCookie(cookie);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final CharSequence name, final CharSequence value) {
        checkSent();
        super.addSetCookie(name, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse context(final ContextMap context) {
        checkSent();
        super.context(context);
        return this;
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
        return payloadWriter;
    }

    @Override
    public <T> HttpPayloadWriter<T> sendMetaData(final HttpSerializer<T> serializer) {
        final HttpPayloadWriter<T> payloadWriter = serializer.serialize(headers(), this.payloadWriter, allocator);
        sendMetaData();
        return payloadWriter;
    }

    @Override
    public <T> HttpPayloadWriter<T> sendMetaData(final HttpStreamingSerializer<T> serializer) {
        final HttpPayloadWriter<T> payloadWriter = serializer.serialize(headers(), this.payloadWriter, allocator);
        sendMetaData();
        return payloadWriter;
    }

    @Override
    public HttpOutputStream sendMetaDataOutputStream() {
        return new HttpPayloadWriterToHttpOutputStream(sendMetaData(), allocator);
    }

    boolean markMetaSent() {
        return metaSentUpdater.compareAndSet(this, 0, 1);
    }

    private static void throwMetaAlreadySent() {
        throw new IllegalStateException("Response meta-data is already sent");
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
        final DefaultBlockingStreamingHttpServerResponse that = (DefaultBlockingStreamingHttpServerResponse) o;
        return metaSent == that.metaSent && sendMeta.equals(that.sendMeta) && payloadWriter.equals(that.payloadWriter)
                && allocator.equals(that.allocator);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + metaSent;
        result = 31 * result + sendMeta.hashCode();
        result = 31 * result + payloadWriter.hashCode();
        result = 31 * result + allocator.hashCode();
        return result;
    }
}
