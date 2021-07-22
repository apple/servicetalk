/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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


import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Common <a href="https://tools.ietf.org/html/rfc7231#section-5">request header values</a> and
 * <a href="https://tools.ietf.org/html/rfc7231#section-7">response header values</a>.
 */
public final class HttpHeaderValues {
    /**
     * {@code "application/json"}
     */
    public static final CharSequence APPLICATION_JSON = newAsciiString("application/json");
    /**
     * {@code "application/x-www-form-urlencoded"}
     */
    public static final CharSequence APPLICATION_X_WWW_FORM_URLENCODED =
            newAsciiString("application/x-www-form-urlencoded");
    /**
     * {@code "application/x-www-form-urlencoded; charset=UTF-8"}
     */
    public static final CharSequence APPLICATION_X_WWW_FORM_URLENCODED_UTF_8 =
            newAsciiString(APPLICATION_X_WWW_FORM_URLENCODED + "; charset=UTF-8");
    /**
     * {@code "base64"}
     */
    public static final CharSequence BASE64 = newAsciiString("base64");
    /**
     * {@code "binary"}
     */
    public static final CharSequence BINARY = newAsciiString("binary");
    /**
     * {@code "boundary"}
     */
    public static final CharSequence BOUNDARY = newAsciiString("boundary");
    /**
     * {@code "bytes"}
     */
    public static final CharSequence BYTES = newAsciiString("bytes");
    /**
     * {@code "charset"}
     */
    public static final CharSequence CHARSET = newAsciiString("charset");
    /**
     * {@code "chunked"}
     */
    public static final CharSequence CHUNKED = newAsciiString("chunked");
    /**
     * {@code "close"}
     */
    public static final CharSequence CLOSE = newAsciiString("close");
    /**
     * {@code "compress"}
     */
    public static final CharSequence COMPRESS = newAsciiString("compress");
    /**
     * {@code "100-continue"}
     */
    public static final CharSequence CONTINUE = newAsciiString("100-continue");
    /**
     * {@code "deflate"}
     */
    public static final CharSequence DEFLATE = newAsciiString("deflate");
    /**
     * {@code "gzip"}
     */
    public static final CharSequence GZIP = newAsciiString("gzip");
    /**
     * {@code "identity"}
     */
    public static final CharSequence IDENTITY = newAsciiString("identity");
    /**
     * {@code "keep-alive"}
     */
    public static final CharSequence KEEP_ALIVE = newAsciiString("keep-alive");
    /**
     * {@code "max-age"}
     */
    public static final CharSequence MAX_AGE = newAsciiString("max-age");
    /**
     * {@code "max-stale"}
     */
    public static final CharSequence MAX_STALE = newAsciiString("max-stale");
    /**
     * {@code "min-fresh"}
     */
    public static final CharSequence MIN_FRESH = newAsciiString("min-fresh");
    /**
     * {@code "multipart/form-data"}
     */
    public static final CharSequence MULTIPART_FORM_DATA = newAsciiString("multipart/form-data");
    /**
     * {@code "must-revalidate"}
     */
    public static final CharSequence MUST_REVALIDATE = newAsciiString("must-revalidate");
    /**
     * {@code "no-cache"}
     */
    public static final CharSequence NO_CACHE = newAsciiString("no-cache");
    /**
     * {@code "no-store"}
     */
    public static final CharSequence NO_STORE = newAsciiString("no-store");
    /**
     * {@code "no-transform"}
     */
    public static final CharSequence NO_TRANSFORM = newAsciiString("no-transform");
    /**
     * {@code "none"}
     */
    public static final CharSequence NONE = newAsciiString("none");
    /**
     * {@code "only-if-cached"}
     */
    public static final CharSequence ONLY_IF_CACHED = newAsciiString("only-if-cached");
    /**
     * {@code "private"}
     */
    public static final CharSequence PRIVATE = newAsciiString("private");
    /**
     * {@code "proxy-revalidate"}
     */
    public static final CharSequence PROXY_REVALIDATE = newAsciiString("proxy-revalidate");
    /**
     * {@code "public"}
     */
    public static final CharSequence PUBLIC = newAsciiString("public");
    /**
     * {@code "quoted-printable"}
     */
    public static final CharSequence QUOTED_PRINTABLE = newAsciiString("quoted-printable");
    /**
     * {@code "s-maxage"}
     */
    public static final CharSequence S_MAXAGE = newAsciiString("s-maxage");
    /**
     * {@code "text/plain"}
     */
    public static final CharSequence TEXT_PLAIN = newAsciiString("text/plain");
    /**
     * {@code "text/plain"}
     */
    public static final CharSequence TEXT_PLAIN_US_ASCII = newAsciiString("text/plain; charset=US-ASCII");
    /**
     * {@code "text/plain"}
     */
    public static final CharSequence TEXT_PLAIN_UTF_8 = newAsciiString("text/plain; charset=UTF-8");
    /**
     * {@code "trailers"}
     */
    public static final CharSequence TRAILERS = newAsciiString("trailers");
    /**
     * {@code "upgrade"}
     */
    public static final CharSequence UPGRADE = newAsciiString("upgrade");
    /**
     * {@code "websocket"}
     */
    public static final CharSequence WEBSOCKET = newAsciiString("websocket");
    /**
     * {@code "XMLHttpRequest"} is a value for {@link HttpHeaderNames#X_REQUESTED_WITH "x-requested-with"} header, which
     * is used by most JavaScript frameworks to identify
     * <a href="https://developer.mozilla.org/en-US/docs/Web/Guide/AJAX">Ajax</a> requests.
     */
    public static final CharSequence XML_HTTP_REQUEST = newAsciiString("XMLHttpRequest");
    /**
     * {@code "0"}
     */
    public static final CharSequence ZERO = newAsciiString("0");

    private HttpHeaderValues() {
        // No instances
    }
}
