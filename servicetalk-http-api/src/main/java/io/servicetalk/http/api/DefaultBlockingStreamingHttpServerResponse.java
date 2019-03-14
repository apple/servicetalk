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
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.SingleProcessor;
import io.servicetalk.concurrent.api.internal.ConnectablePayloadWriter;
import io.servicetalk.concurrent.internal.ThreadInterruptingCancellable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponseWithTrailers;
import static java.util.Objects.requireNonNull;

final class DefaultBlockingStreamingHttpServerResponse extends BlockingStreamingHttpServerResponse {

    private static final AtomicIntegerFieldUpdater<DefaultBlockingStreamingHttpServerResponse> sentUpdater =
            AtomicIntegerFieldUpdater.newUpdater(DefaultBlockingStreamingHttpServerResponse.class, "sent");

    @SuppressWarnings("unused")
    private volatile int sent;
    private final HttpHeaders trailers;
    private final BufferAllocator allocator;
    private final SingleProcessor<StreamingHttpResponse> responseProcessor;
    private final ThreadInterruptingCancellable tiCancellable;
    private final CompletableProcessor payloadProcessor = new CompletableProcessor();

    DefaultBlockingStreamingHttpServerResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                               final HttpHeaders headers, final HttpHeaders trailers,
                                               final BufferAllocator allocator,
                                               final SingleProcessor<StreamingHttpResponse> responseProcessor,
                                               final ThreadInterruptingCancellable tiCancellable) {
        super(status, version, headers, allocator);
        this.trailers = requireNonNull(trailers);
        this.allocator = requireNonNull(allocator);
        this.responseProcessor = requireNonNull(responseProcessor);
        this.tiCancellable = requireNonNull(tiCancellable);
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
        if (sent != 0) {
            throwSent();
        }
    }

    private void throwSent() {
        final IllegalStateException e = new IllegalStateException("Response meta-data is already sent");
        payloadProcessor.onError(e);
        throw e;
    }

    @Override
    public HttpPayloadWriter<Buffer> sendMetaData() {
        if (!sentUpdater.compareAndSet(this, 0, 1)) {
            throwSent();
        }

        final BufferHttpPayloadWriter pw = new BufferHttpPayloadWriter(trailers, payloadProcessor);
        final Publisher<Object> payloadBodyAndTrailers = payloadProcessor.merge(pw.connect()
                .map(buffer -> (Object) buffer) // down cast to Object
                .concatWith(success(trailers)));

        final StreamingHttpResponse response = newResponseWithTrailers(status(), version(), headers(), allocator,
                payloadBodyAndTrailers);
        tiCancellable.setDone();
        responseProcessor.onSuccess(response);
        return pw;
    }

    private static final class BufferHttpPayloadWriter implements HttpPayloadWriter<Buffer> {

        private final ConnectablePayloadWriter<Buffer> payloadWriter = new ConnectablePayloadWriter<>();
        private final HttpHeaders trailers;
        private final CompletableProcessor payloadProcessor;

        BufferHttpPayloadWriter(final HttpHeaders trailers, final CompletableProcessor payloadProcessor) {
            this.trailers = trailers;
            this.payloadProcessor = payloadProcessor;
        }

        @Override
        public void write(final Buffer object) throws IOException {
            payloadWriter.write(object);
        }

        @Override
        public void flush() throws IOException {
            payloadWriter.flush();
        }

        @Override
        public void close() throws IOException {
            payloadWriter.close();
            payloadProcessor.onComplete();
        }

        @Override
        public HttpHeaders trailers() {
            return trailers;
        }

        Publisher<Buffer> connect() {
            return payloadWriter.connect();
        }
    }
}
