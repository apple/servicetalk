/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.Http2ErrorCode;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.transport.api.RetryableException;

import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.handler.codec.http2.Http2FrameStream;

import static io.netty.handler.codec.http2.Http2Error.REFUSED_STREAM;

final class NettyHttp2ExceptionUtils {
    private NettyHttp2ExceptionUtils() {
    }

    static Throwable wrapIfNecessary(final Throwable cause) {
        if (cause instanceof io.netty.handler.codec.http2.Http2Exception) {
            final int streamId = (cause instanceof StreamException) ? ((StreamException) cause).streamId() : 0;
            final io.netty.handler.codec.http2.Http2Exception h2Cause =
                    (io.netty.handler.codec.http2.Http2Exception) cause;
            if (h2Cause.error() == REFUSED_STREAM) {
                // The first check captures cases like:
                //  - Http2ChannelClosedException.
                //  - Cannot create stream %d greater than Last-Stream-ID %d from GOAWAY.
                //  - Stream IDs are exhausted for this endpoint.
                //  - Maximum active streams violated for this endpoint.
                if (cause.getMessage() != null &&
                        cause.getMessage().startsWith("Maximum active streams violated for this endpoint")) {
                    return new MaxConcurrentStreamsViolatedStacklessHttp2Exception(streamId, h2Cause);
                }
            } else if (h2Cause instanceof io.netty.handler.codec.http2.Http2NoMoreStreamIdsException) {
                // The  second check captures "No more streams can be created on this connection":
                return new RetryableStacklessHttp2Exception(streamId, h2Cause);
            }
            return new StacklessHttp2Exception(streamId, h2Cause);
        }
        if (cause instanceof io.netty.handler.codec.http2.Http2FrameStreamException) {
            io.netty.handler.codec.http2.Http2FrameStreamException streamException =
                    (io.netty.handler.codec.http2.Http2FrameStreamException) cause;
            return isRetryable(streamException) ?
                    new RetryableStacklessHttp2Exception(streamException.stream().id(), streamException) :
                    new StacklessHttp2Exception(streamException.stream().id(), streamException);
        }
        return cause;
    }

    static class H2StreamResetException extends Http2Exception {
        private static final long serialVersionUID = -7096164907438998924L;

        H2StreamResetException(int streamId, int errorCode) {
            this(streamId, stErrorCode(errorCode));
        }

        H2StreamResetException(int streamId, Http2ErrorCode errorCode) {
            super(streamId, errorCode, "RST_STREAM received for streamId=" + streamId +
                    " with error code: " + errorCode);
        }
    }

    static H2StreamResetException newStreamResetException(
            final io.netty.handler.codec.http2.Http2ResetFrame resetFrame) {
        final Http2FrameStream stream = resetFrame.stream();
        assert stream != null;
        return resetFrame.errorCode() == REFUSED_STREAM.code() ?
                new H2StreamRefusedException(stream.id()) :
                new H2StreamResetException(stream.id(), (int) resetFrame.errorCode());
    }

    private static boolean isRetryable(final io.netty.handler.codec.http2.Http2FrameStreamException cause) {
        return cause.error() == REFUSED_STREAM;
    }

    private static final class StacklessHttp2Exception extends Http2Exception {
        private static final long serialVersionUID = 7120356856138442469L;

        StacklessHttp2Exception(int streamId, io.netty.handler.codec.http2.Http2Exception cause) {
            super(streamId, nettyToStErrorCode(cause.error()), cause);
        }

        StacklessHttp2Exception(int streamId, io.netty.handler.codec.http2.Http2FrameStreamException cause) {
            super(streamId, nettyToStErrorCode(cause.error()), cause);
        }

        @Override
        public Throwable fillInStackTrace() {
            // This is a wrapping exception class that always has an original cause and does not require stack trace.
            return this;
        }
    }

    private static class RetryableStacklessHttp2Exception extends Http2Exception implements RetryableException {
        private static final long serialVersionUID = -413341269442893267L;

        RetryableStacklessHttp2Exception(final int streamId, io.netty.handler.codec.http2.Http2Exception cause) {
            super(streamId, nettyToStErrorCode(cause.error()), cause);
        }

        RetryableStacklessHttp2Exception(final int streamId,
                                         io.netty.handler.codec.http2.Http2FrameStreamException cause) {
            super(streamId, nettyToStErrorCode(cause.error()), cause);
        }

        @Override
        public Throwable fillInStackTrace() {
            // This is a wrapping exception class that always has an original cause and does not require stack trace.
            return this;
        }
    }

    static final class MaxConcurrentStreamsViolatedStacklessHttp2Exception extends RetryableStacklessHttp2Exception {
        private static final long serialVersionUID = 5519486857188675226L;

        MaxConcurrentStreamsViolatedStacklessHttp2Exception(final int streamId,
                                                            io.netty.handler.codec.http2.Http2Exception cause) {
            super(streamId, cause);
        }
    }

    /**
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.4">REFUSED_STREAM</a> is always retryable.
     */
    private static final class H2StreamRefusedException extends H2StreamResetException implements RetryableException {
        private static final long serialVersionUID = -2151927266051609262L;

        H2StreamRefusedException(int streamId) {
            super(streamId, Http2ErrorCode.REFUSED_STREAM);
        }
    }

    private static Http2ErrorCode nettyToStErrorCode(Http2Error error) {
        return stErrorCode((int) error.code());
    }

    private static Http2ErrorCode stErrorCode(int code) {
        Http2ErrorCode errorCode = Http2ErrorCode.of(code);
        return errorCode == null ? Http2ErrorCode.of(code, String.valueOf(code)) : errorCode;
    }
}
