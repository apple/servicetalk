/**
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

/**
 * Common <a href="https://tools.ietf.org/html/rfc7231#section-5">request header values</a> and
 * <a href="https://tools.ietf.org/html/rfc7231#section-7">response header values</a>.
 */
public final class HttpHeaderValues {
    /**
     * {@code "application/x-www-form-urlencoded"}
     */
    public static final CharSequence APPLICATION_X_WWW_FORM_URLENCODED =
            io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
    /**
     * {@code "base64"}
     */
    public static final CharSequence BASE64 = io.netty.handler.codec.http.HttpHeaderValues.BASE64;
    /**
     * {@code "binary"}
     */
    public static final CharSequence BINARY = io.netty.handler.codec.http.HttpHeaderValues.BINARY;
    /**
     * {@code "boundary"}
     */
    public static final CharSequence BOUNDARY = io.netty.handler.codec.http.HttpHeaderValues.BOUNDARY;
    /**
     * {@code "bytes"}
     */
    public static final CharSequence BYTES = io.netty.handler.codec.http.HttpHeaderValues.BYTES;
    /**
     * {@code "charset"}
     */
    public static final CharSequence CHARSET = io.netty.handler.codec.http.HttpHeaderValues.CHARSET;
    /**
     * {@code "chunked"}
     */
    public static final CharSequence CHUNKED = io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;
    /**
     * {@code "close"}
     */
    public static final CharSequence CLOSE = io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
    /**
     * {@code "compress"}
     */
    public static final CharSequence COMPRESS = io.netty.handler.codec.http.HttpHeaderValues.COMPRESS;
    /**
     * {@code "100-continue"}
     */
    public static final CharSequence CONTINUE = io.netty.handler.codec.http.HttpHeaderValues.CONTINUE;
    /**
     * {@code "deflate"}
     */
    public static final CharSequence DEFLATE = io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
    /**
     * {@code "gzip"}
     */
    public static final CharSequence GZIP = io.netty.handler.codec.http.HttpHeaderValues.GZIP;
    /**
     * {@code "identity"}
     */
    public static final CharSequence IDENTITY = io.netty.handler.codec.http.HttpHeaderValues.IDENTITY;
    /**
     * {@code "keep-alive"}
     */
    public static final CharSequence KEEP_ALIVE = io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
    /**
     * {@code "max-age"}
     */
    public static final CharSequence MAX_AGE = io.netty.handler.codec.http.HttpHeaderValues.MAX_AGE;
    /**
     * {@code "max-stale"}
     */
    public static final CharSequence MAX_STALE = io.netty.handler.codec.http.HttpHeaderValues.MAX_STALE;
    /**
     * {@code "min-fresh"}
     */
    public static final CharSequence MIN_FRESH = io.netty.handler.codec.http.HttpHeaderValues.MIN_FRESH;
    /**
     * {@code "multipart/form-data"}
     */
    public static final CharSequence MULTIPART_FORM_DATA = io.netty.handler.codec.http.HttpHeaderValues.MULTIPART_FORM_DATA;
    /**
     * {@code "must-revalidate"}
     */
    public static final CharSequence MUST_REVALIDATE = io.netty.handler.codec.http.HttpHeaderValues.MUST_REVALIDATE;
    /**
     * {@code "no-cache"}
     */
    public static final CharSequence NO_CACHE = io.netty.handler.codec.http.HttpHeaderValues.NO_CACHE;
    /**
     * {@code "no-store"}
     */
    public static final CharSequence NO_STORE = io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;
    /**
     * {@code "no-transform"}
     */
    public static final CharSequence NO_TRANSFORM = io.netty.handler.codec.http.HttpHeaderValues.NO_TRANSFORM;
    /**
     * {@code "none"}
     */
    public static final CharSequence NONE = io.netty.handler.codec.http.HttpHeaderValues.NONE;
    /**
     * {@code "only-if-cached"}
     */
    public static final CharSequence ONLY_IF_CACHED = io.netty.handler.codec.http.HttpHeaderValues.ONLY_IF_CACHED;
    /**
     * {@code "private"}
     */
    public static final CharSequence PRIVATE = io.netty.handler.codec.http.HttpHeaderValues.PRIVATE;
    /**
     * {@code "proxy-revalidate"}
     */
    public static final CharSequence PROXY_REVALIDATE = io.netty.handler.codec.http.HttpHeaderValues.PROXY_REVALIDATE;
    /**
     * {@code "public"}
     */
    public static final CharSequence PUBLIC = io.netty.handler.codec.http.HttpHeaderValues.PUBLIC;
    /**
     * {@code "quoted-printable"}
     */
    public static final CharSequence QUOTED_PRINTABLE = io.netty.handler.codec.http.HttpHeaderValues.QUOTED_PRINTABLE;
    /**
     * {@code "s-maxage"}
     */
    public static final CharSequence S_MAXAGE = io.netty.handler.codec.http.HttpHeaderValues.S_MAXAGE;
    /**
     * {@code "trailers"}
     */
    public static final CharSequence TRAILERS = io.netty.handler.codec.http.HttpHeaderValues.TRAILERS;
    /**
     * {@code "Upgrade"}
     */
    public static final CharSequence UPGRADE = io.netty.handler.codec.http.HttpHeaderValues.UPGRADE;
    /**
     * {@code "WebSocket"}
     */
    public static final CharSequence WEBSOCKET = io.netty.handler.codec.http.HttpHeaderValues.WEBSOCKET;
    /**
     * {@code 0}
     */
    public static final CharSequence ZERO = io.netty.handler.codec.http.HttpHeaderValues.ZERO;
    /**
     * {@code "text/plain"}
     */
    public static final CharSequence TEXT_PLAIN = io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;

    private HttpHeaderValues() {
    }
}
