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

import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

/**
 * The equivalent of {@link HttpResponse} but provides an ability to write the payload to an {@link HttpPayloadWriter}.
 *
 * @see BlockingStreamingHttpService
 */
public abstract class BlockingStreamingHttpServerResponse extends DefaultHttpResponseMetaData {

    private final HttpPayloadWriter<Buffer> payloadWriter;
    private final BufferAllocator allocator;

    /**
     * Creates a new instance.
     *
     * @param status a status for the response
     * @param version a default version for the response
     * @param headers an {@link HttpHeaders} object for headers
     * @param allocator a {@link BufferAllocator} to use for {@link #sendMetaData(HttpStreamingSerializer)}.
     */
    BlockingStreamingHttpServerResponse(final HttpResponseStatus status,
                                        final HttpProtocolVersion version,
                                        final HttpHeaders headers,
                                        final HttpPayloadWriter<Buffer> payloadWriter,
                                        final BufferAllocator allocator) {
        super(status, version, headers);
        this.payloadWriter = requireNonNull(payloadWriter);
        this.allocator = requireNonNull(allocator);
    }

    /**
     * Sends the {@link HttpResponseMetaData} and returns an {@link HttpPayloadWriter} to continue writing the payload
     * body.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     *
     * @return {@link HttpPayloadWriter} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     */
    public abstract HttpPayloadWriter<Buffer> sendMetaData();

    /**
     * Sends the {@link HttpResponseMetaData} to the client and returns an {@link HttpPayloadWriter} of type {@link T}
     * to continue writing a payload body. Each element will be serialized using provided {@code serializer}.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     * @deprecated Use {@link #sendMetaData(HttpStreamingSerializer)}.
     * @param serializer used to serialize the payload elements
     * @param <T> the type of objects to write
     * @return {@link HttpPayloadWriter} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     */
    @Deprecated
    public final <T> HttpPayloadWriter<T> sendMetaData(final HttpSerializer<T> serializer) {
        final HttpPayloadWriter<T> payloadWriter = serializer.serialize(headers(), this.payloadWriter, allocator);
        sendMetaData();
        return payloadWriter;
    }

    /**
     * Sends the {@link HttpResponseMetaData} to the client and returns an {@link HttpPayloadWriter} of type {@link T}
     * to continue writing a payload body. Each element will be serialized using provided {@code serializer}.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     *
     * @param serializer used to serialize the payload elements
     * @param <T> the type of objects to write
     * @return {@link HttpPayloadWriter} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     */
    public final <T> HttpPayloadWriter<T> sendMetaData(final HttpStreamingSerializer<T> serializer) {
        final HttpPayloadWriter<T> payloadWriter = serializer.serialize(headers(), this.payloadWriter, allocator);
        sendMetaData();
        return payloadWriter;
    }

    /**
     * Sends the {@link HttpResponseMetaData} to the client and returns an {@link OutputStream} to continue writing a
     * payload body.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed.
     *
     * @return {@link HttpOutputStream} to write a payload body
     * @throws IllegalStateException if one of the {@code sendMetaData*} methods has been called on this response
     */
    public final HttpOutputStream sendMetaDataOutputStream() {
        return new HttpPayloadWriterToHttpOutputStream(sendMetaData(), allocator);
    }

    @Override
    public BlockingStreamingHttpServerResponse version(HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse status(HttpResponseStatus status) {
        super.status(status);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addHeader(final CharSequence name, final CharSequence value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addHeaders(final HttpHeaders headers) {
        super.addHeaders(headers);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse setHeader(final CharSequence name, final CharSequence value) {
        super.setHeader(name, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse setHeaders(final HttpHeaders headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addCookie(final HttpCookiePair cookie) {
        super.addCookie(cookie);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addCookie(final CharSequence name, final CharSequence value) {
        super.addCookie(name, value);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final HttpSetCookie cookie) {
        super.addSetCookie(cookie);
        return this;
    }

    @Override
    public BlockingStreamingHttpServerResponse addSetCookie(final CharSequence name, final CharSequence value) {
        super.addSetCookie(name, value);
        return this;
    }

    final HttpPayloadWriter<Buffer> payloadWriter() {
        return payloadWriter;
    }
}
