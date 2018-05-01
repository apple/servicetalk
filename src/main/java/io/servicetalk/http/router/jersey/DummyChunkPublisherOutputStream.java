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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static java.util.Objects.requireNonNull;

/**
 * Dummy adapter between {@link java.io.OutputStream} and {@link Publisher} of {@link HttpPayloadChunk}.
 */
final class DummyChunkPublisherOutputStream extends ByteArrayOutputStream {
    private final BufferAllocator allocator;

    @Nullable
    private volatile Subscriber<? super HttpPayloadChunk> subscriber;

    @Nullable
    private volatile HttpPayloadChunk content;

    DummyChunkPublisherOutputStream(final BufferAllocator allocator) {
        this.allocator = requireNonNull(allocator);
    }

    Publisher<HttpPayloadChunk> getChunkPublisher() {
        return new Publisher<HttpPayloadChunk>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super HttpPayloadChunk> subscriber) {
                DummyChunkPublisherOutputStream.this.subscriber = subscriber;
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long l) {
                        // close() may have been called already
                        tryEmitContent();
                    }

                    @Override
                    public void cancel() {
                        // NOOP
                    }
                });
            }
        };
    }

    @Override
    public void close() throws IOException {
        super.close();

        content = newPayloadChunk(allocator.wrap(toByteArray()));
        tryEmitContent();
    }

    private void tryEmitContent() {
        final HttpPayloadChunk c = content;
        final Subscriber<? super HttpPayloadChunk> s = subscriber;
        if (c != null && s != null) {
            s.onNext(newPayloadChunk(allocator.wrap(toByteArray())));
            s.onComplete();
        }
    }
}
