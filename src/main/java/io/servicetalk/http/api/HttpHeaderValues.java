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
package io.servicetalk.http.api;

import static io.servicetalk.http.api.CharSequences.newCaseInsensitiveAsciiString;

/**
 * Common <a href="https://tools.ietf.org/html/rfc7231#section-5">request header values</a> and
 * <a href="https://tools.ietf.org/html/rfc7231#section-7">response header values</a>.
 */
public final class HttpHeaderValues {
    /**
     * {@code "application/x-www-form-urlencoded"}
     */
    public static final CharSequence APPLICATION_X_WWW_FORM_URLENCODED =
            newCaseInsensitiveAsciiString("application/x-www-form-urlencoded");
    /**
     * {@code "base64"}
     */
    public static final CharSequence BASE64 = newCaseInsensitiveAsciiString("base64");
    /**
     * {@code "binary"}
     */
    public static final CharSequence BINARY = newCaseInsensitiveAsciiString("binary");
    /**
     * {@code "boundary"}
     */
    public static final CharSequence BOUNDARY = newCaseInsensitiveAsciiString("boundary");
    /**
     * {@code "bytes"}
     */
    public static final CharSequence BYTES = newCaseInsensitiveAsciiString("bytes");
    /**
     * {@code "charset"}
     */
    public static final CharSequence CHARSET = newCaseInsensitiveAsciiString("charset");
    /**
     * {@code "chunked"}
     */
    public static final CharSequence CHUNKED = newCaseInsensitiveAsciiString("chunked");
    /**
     * {@code "close"}
     */
    public static final CharSequence CLOSE = newCaseInsensitiveAsciiString("close");
    /**
     * {@code "compress"}
     */
    public static final CharSequence COMPRESS = newCaseInsensitiveAsciiString("compress");
    /**
     * {@code "100-continue"}
     */
    public static final CharSequence CONTINUE = newCaseInsensitiveAsciiString("100-continue");
    /**
     * {@code "deflate"}
     */
    public static final CharSequence DEFLATE = newCaseInsensitiveAsciiString("deflate");
    /**
     * {@code "gzip"}
     */
    public static final CharSequence GZIP = newCaseInsensitiveAsciiString("gzip");
    /**
     * {@code "identity"}
     */
    public static final CharSequence IDENTITY = newCaseInsensitiveAsciiString("identity");
    /**
     * {@code "keep-alive"}
     */
    public static final CharSequence KEEP_ALIVE = newCaseInsensitiveAsciiString("keep-alive");
    /**
     * {@code "max-age"}
     */
    public static final CharSequence MAX_AGE = newCaseInsensitiveAsciiString("max-age");
    /**
     * {@code "max-stale"}
     */
    public static final CharSequence MAX_STALE = newCaseInsensitiveAsciiString("max-stale");
    /**
     * {@code "min-fresh"}
     */
    public static final CharSequence MIN_FRESH = newCaseInsensitiveAsciiString("min-fresh");
    /**
     * {@code "multipart/form-data"}
     */
    public static final CharSequence MULTIPART_FORM_DATA = newCaseInsensitiveAsciiString("multipart/form-data");
    /**
     * {@code "must-revalidate"}
     */
    public static final CharSequence MUST_REVALIDATE = newCaseInsensitiveAsciiString("must-revalidate");
    /**
     * {@code "no-cache"}
     */
    public static final CharSequence NO_CACHE = newCaseInsensitiveAsciiString("no-cache");
    /**
     * {@code "no-store"}
     */
    public static final CharSequence NO_STORE = newCaseInsensitiveAsciiString("no-store");
    /**
     * {@code "no-transform"}
     */
    public static final CharSequence NO_TRANSFORM = newCaseInsensitiveAsciiString("no-transform");
    /**
     * {@code "none"}
     */
    public static final CharSequence NONE = newCaseInsensitiveAsciiString("none");
    /**
     * {@code "only-if-cached"}
     */
    public static final CharSequence ONLY_IF_CACHED = newCaseInsensitiveAsciiString("only-if-cached");
    /**
     * {@code "private"}
     */
    public static final CharSequence PRIVATE = newCaseInsensitiveAsciiString("private");
    /**
     * {@code "proxy-revalidate"}
     */
    public static final CharSequence PROXY_REVALIDATE = newCaseInsensitiveAsciiString("proxy-revalidate");
    /**
     * {@code "public"}
     */
    public static final CharSequence PUBLIC = newCaseInsensitiveAsciiString("public");
    /**
     * {@code "quoted-printable"}
     */
    public static final CharSequence QUOTED_PRINTABLE = newCaseInsensitiveAsciiString("quoted-printable");
    /**
     * {@code "s-maxage"}
     */
    public static final CharSequence S_MAXAGE = newCaseInsensitiveAsciiString("s-maxage");
    /**
     * {@code "text/plain"}
     */
    public static final CharSequence TEXT_PLAIN = newCaseInsensitiveAsciiString("text/plain");
    /**
     * {@code "trailers"}
     */
    public static final CharSequence TRAILERS = newCaseInsensitiveAsciiString("trailers");
    /**
     * {@code "upgrade"}
     */
    public static final CharSequence UPGRADE = newCaseInsensitiveAsciiString("upgrade");
    /**
     * {@code "websocket"}
     */
    public static final CharSequence WEBSOCKET = newCaseInsensitiveAsciiString("websocket");
    /**
     * {@code "0"}
     */
    public static final CharSequence ZERO = newCaseInsensitiveAsciiString("0");

    private HttpHeaderValues() {
        // No instances
    }
}
