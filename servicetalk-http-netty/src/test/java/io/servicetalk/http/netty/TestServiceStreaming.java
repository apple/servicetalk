/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.ConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;

final class TestServiceStreaming implements StreamingHttpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestServiceStreaming.class);

    static final String SVC_ECHO = "/echo";
    static final String SVC_COUNTER_NO_LAST_CHUNK = "/counterNoLastChunk";
    static final String SVC_COUNTER = "/counter";
    static final String SVC_LARGE_LAST = "/largeLast";
    static final String SVC_TEST_PUBLISHER = "/testPublisher";
    static final String SVC_NO_CONTENT = "/noContent";
    static final String SVC_NO_CONTENT_AFTER_READ = "/noContentAfterRead";
    static final String SVC_ROT13 = "/rot13";
    static final String SVC_THROW_ERROR = "/throwError";
    static final String SVC_SINGLE_ERROR = "/singleError";
    static final String SVC_ERROR_BEFORE_READ = "/errorBeforeRead";
    static final String SVC_ERROR_DURING_READ = "/errorDuringRead";
    private final Function<StreamingHttpRequest, Publisher<Buffer>> publisherSupplier;

    private int counter;

    TestServiceStreaming() {
        this(__ -> empty());
    }

    TestServiceStreaming(final Function<StreamingHttpRequest, Publisher<Buffer>> publisherSupplier) {
        this.publisherSupplier = publisherSupplier;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext context,
                                                final StreamingHttpRequest req,
                                                final StreamingHttpResponseFactory factory) {
        LOGGER.debug("({}) Handling {}", counter, req.toString((a, b) -> b));
        final StreamingHttpResponse response;
        switch (req.path()) {
            case SVC_ECHO:
                response = newEchoResponse(req, factory);
                break;
            case SVC_COUNTER_NO_LAST_CHUNK:
                response = newTestCounterResponse(context, req, factory);
                break;
            case SVC_COUNTER:
                response = newTestCounterResponseWithLastPayloadChunk(context, req, factory);
                break;
            case SVC_LARGE_LAST:
                response = newLargeLastChunkResponse(context, req, factory);
                break;
            case SVC_TEST_PUBLISHER:
                response = newTestPublisherResponse(req, factory);
                break;
            case SVC_NO_CONTENT:
                response = newNoContentResponse(req, factory);
                break;
            case SVC_NO_CONTENT_AFTER_READ:
                return req.payloadBody().ignoreElements().concat(succeeded(newNoContentResponse(req, factory)));
            case SVC_ROT13:
                response = newRot13Response(req, factory);
                break;
            case SVC_THROW_ERROR:
                response = throwErrorSynchronously();
                break;
            case SVC_SINGLE_ERROR:
                return Single.failed(DELIBERATE_EXCEPTION);
            case SVC_ERROR_BEFORE_READ:
                response = throwErrorBeforeRead(req, factory);
                break;
            case SVC_ERROR_DURING_READ:
                response = throwErrorDuringRead(req, factory);
                break;
            default:
                response = newNotFoundResponse(req, factory);
        }
        return succeeded(response);
    }

    private static StreamingHttpResponse newEchoResponse(final StreamingHttpRequest req,
                                                         final StreamingHttpResponseFactory factory) {
        final StreamingHttpResponse response = factory.ok().version(req.version())
                .payloadBody(req.payloadBody());
        final CharSequence contentLength = req.headers().get(CONTENT_LENGTH);
        if (contentLength != null) {
            response.headers().set(CONTENT_LENGTH, contentLength);
        }
        final CharSequence contentType = req.headers().get(CONTENT_TYPE);
        if (contentType != null) {
            response.headers().set(CONTENT_TYPE, contentType);
        }
        return response;
    }

    private StreamingHttpResponse newTestCounterResponse(final ConnectionContext context,
                                                         final StreamingHttpRequest req,
                                                         final StreamingHttpResponseFactory factory) {
        final Buffer responseContent = context.executionContext().bufferAllocator().fromUtf8(
                "Testing" + ++counter + "\n");
        return factory.ok().version(req.version()).payloadBody(from(responseContent));
    }

    private StreamingHttpResponse newTestCounterResponseWithLastPayloadChunk(
            final ConnectionContext context, final StreamingHttpRequest req,
            final StreamingHttpResponseFactory factory) {
        final Buffer responseContent = context.executionContext().bufferAllocator().fromUtf8(
                "Testing" + ++counter + "\n");
        return factory.ok().version(req.version()).payloadBody(from(responseContent));
    }

    private static StreamingHttpResponse newLargeLastChunkResponse(
            final ConnectionContext context, final StreamingHttpRequest req,
            final StreamingHttpResponseFactory factory) {
        final byte[] content = new byte[1024];
        ThreadLocalRandom.current().nextBytes(content);
        final Buffer chunk = context.executionContext().bufferAllocator().wrap(content);

        final byte[] lastContent = new byte[6144];
        ThreadLocalRandom.current().nextBytes(lastContent);
        final Buffer lastChunk = context.executionContext().bufferAllocator().wrap(lastContent);

        return factory.ok().version(req.version()).payloadBody(from(chunk, lastChunk));
    }

    private StreamingHttpResponse newTestPublisherResponse(
            final StreamingHttpRequest req, final StreamingHttpResponseFactory factory) {
        return factory.ok().version(req.version()).payloadBody(publisherSupplier.apply(req));
    }

    private static StreamingHttpResponse newNoContentResponse(final StreamingHttpRequest req,
                                                              final StreamingHttpResponseFactory factory) {
        return factory.newResponse(NO_CONTENT).version(req.version());
    }

    private static StreamingHttpResponse newRot13Response(final StreamingHttpRequest req,
                                                          final StreamingHttpResponseFactory factory) {
        final Publisher<Buffer> responseBody = req.payloadBody().map(buffer -> {
            // Do an ASCII-only ROT13
            for (int i = buffer.readerIndex(); i < buffer.writerIndex(); i++) {
                final byte c = buffer.getByte(i);
                if (c >= 'a' && c <= 'm' || c >= 'A' && c <= 'M') {
                    buffer.setByte(i, c + 13);
                } else if (c >= 'n' && c <= 'z' || c >= 'N' && c <= 'Z') {
                    buffer.setByte(i, c - 13);
                }
            }
            return buffer;
        });
        return factory.ok().version(req.version()).payloadBody(responseBody);
    }

    private static StreamingHttpResponse newNotFoundResponse(final StreamingHttpRequest req,
                                                             final StreamingHttpResponseFactory factory) {
        return factory.newResponse(NOT_FOUND).version(req.version());
    }

    private static StreamingHttpResponse throwErrorSynchronously() {
        throw DELIBERATE_EXCEPTION;
    }

    private static StreamingHttpResponse throwErrorBeforeRead(final StreamingHttpRequest req,
                                                              final StreamingHttpResponseFactory factory) {
        return factory.ok().version(req.version()).payloadBody(Publisher.failed(
                DELIBERATE_EXCEPTION));
    }

    private static StreamingHttpResponse throwErrorDuringRead(final StreamingHttpRequest req,
                                                              final StreamingHttpResponseFactory factory) {
        return factory.ok().version(req.version()).payloadBody(
                req.payloadBody().concat(Completable.failed(DELIBERATE_EXCEPTION)));
    }
}
