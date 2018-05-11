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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpPayloadChunks;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatuses;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.LastHttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;

final class TestService extends HttpService<HttpPayloadChunk, HttpPayloadChunk> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestService.class);

    static final String SVC_ECHO = "/echo";
    static final String SVC_COUNTER_NO_LAST_CHUNK = "/counterNoLastChunk";
    static final String SVC_COUNTER = "/counter";
    static final String SVC_NO_CONTENT = "/noContent";
    static final String SVC_ROT13 = "/rot13";
    static final String SVC_THROW_ERROR = "/throwError";
    static final String SVC_SINGLE_ERROR = "/singleError";
    static final String SVC_ERROR_BEFORE_READ = "/errorBeforeRead";
    static final String SVC_ERROR_DURING_READ = "/errorDuringRead";

    private int counter;
    private final Executor executor;

    TestService(final Executor executor) {
        this.executor = executor;
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext context,
                                                         final HttpRequest<HttpPayloadChunk> req) {
        LOGGER.debug("({}) Handling {}", counter, req.toString((a, b) -> b));
        final HttpResponse<HttpPayloadChunk> response;
        switch (req.getPath()) {
            case SVC_ECHO:
                response = newEchoResponse(req);
                break;
            case SVC_COUNTER_NO_LAST_CHUNK:
                response = newTestCounterResponse(context, req);
                break;
            case SVC_COUNTER:
                response = newTestCounterResponseWithLastPayloadChunk(context, req);
                break;
            case SVC_NO_CONTENT:
                response = newNoContentResponse(req);
                break;
            case SVC_ROT13:
                response = newRot13Response(req);
                break;
            case SVC_THROW_ERROR:
                response = throwErrorSynchronously();
                break;
            case SVC_SINGLE_ERROR:
                return Single.error(DELIBERATE_EXCEPTION);
            case SVC_ERROR_BEFORE_READ:
                response = throwErrorBeforeRead(req);
                break;
            case SVC_ERROR_DURING_READ:
                response = throwErrorDuringRead(req);
                break;
            default:
                response = newNotFoundResponse(req);
        }
        return Single.success(response);
    }

    private HttpResponse<HttpPayloadChunk> newEchoResponse(final HttpRequest<HttpPayloadChunk> req) {
        final HttpResponse<HttpPayloadChunk> response = newResponse(req.getVersion(), OK, req.getPayloadBody());
        final CharSequence contentLength = req.getHeaders().get(CONTENT_LENGTH);
        if (contentLength != null) {
            response.getHeaders().set(CONTENT_LENGTH, contentLength);
        }
        return response;
    }

    private HttpResponse<HttpPayloadChunk> newTestCounterResponse(final ConnectionContext context,
                                                                  final HttpRequest<HttpPayloadChunk> req) {
        final Buffer responseContent = context.getBufferAllocator().fromUtf8(
                "Testing" + ++counter + "\n");
        final HttpPayloadChunk responseBody = HttpPayloadChunks.newPayloadChunk(responseContent);
        return newResponse(req.getVersion(), OK, responseBody, executor);
    }

    private HttpResponse<HttpPayloadChunk> newTestCounterResponseWithLastPayloadChunk(
            final ConnectionContext context, final HttpRequest<HttpPayloadChunk> req) {
        final Buffer responseContent = context.getBufferAllocator().fromUtf8(
                "Testing" + ++counter + "\n");
        final HttpPayloadChunk responseBody = HttpPayloadChunks.newLastPayloadChunk(responseContent,
                INSTANCE.newEmptyTrailers());
        return newResponse(req.getVersion(), OK, responseBody, executor);
    }

    private HttpResponse<HttpPayloadChunk> newNoContentResponse(final HttpRequest<HttpPayloadChunk> req) {
        return newResponse(req.getVersion(), HttpResponseStatuses.NO_CONTENT, executor);
    }

    private HttpResponse<HttpPayloadChunk> newRot13Response(final HttpRequest<HttpPayloadChunk> req) {
        final Publisher<HttpPayloadChunk> responseBody = req.getPayloadBody().map(chunk -> {
            final Buffer buffer = chunk.getContent();
            // Do an ASCII-only ROT13
            for (int i = buffer.getReaderIndex(); i < buffer.getWriterIndex(); i++) {
                final byte c = buffer.getByte(i);
                if (c >= 'a' && c <= 'm' || c >= 'A' && c <= 'M') {
                    buffer.setByte(i, c + 13);
                } else if (c >= 'n' && c <= 'z' || c >= 'N' && c <= 'Z') {
                    buffer.setByte(i, c - 13);
                }
            }
            return chunk;
        });
        return newResponse(req.getVersion(), OK, responseBody);
    }

    private HttpResponse<HttpPayloadChunk> newNotFoundResponse(final HttpRequest<HttpPayloadChunk> req) {
        return newResponse(req.getVersion(), NOT_FOUND, executor);
    }

    private HttpResponse<HttpPayloadChunk> throwErrorSynchronously() {
        throw DELIBERATE_EXCEPTION;
    }

    private HttpResponse<HttpPayloadChunk> throwErrorBeforeRead(final HttpRequest<HttpPayloadChunk> req) {
        final Publisher<HttpPayloadChunk> responseBodyPublisher = Publisher.error(
                DELIBERATE_EXCEPTION, executor);
        return newResponse(req.getVersion(), OK, responseBodyPublisher);
    }

    private HttpResponse<HttpPayloadChunk> throwErrorDuringRead(final HttpRequest<HttpPayloadChunk> req) {
        final Publisher<HttpPayloadChunk> responseBodyPublisher = req.getPayloadBody()
                .filter(reqChunk -> !(reqChunk instanceof LastHttpPayloadChunk))
                .concatWith(error(DELIBERATE_EXCEPTION));

        return newResponse(req.getVersion(), OK, responseBodyPublisher);
    }
}
