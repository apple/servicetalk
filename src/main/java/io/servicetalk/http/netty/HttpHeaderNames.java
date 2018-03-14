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
 * Common <a href="https://tools.ietf.org/html/rfc7231#section-5">request header names</a> and
 * <a href="https://tools.ietf.org/html/rfc7231#section-7">response header names</a>.
 */
public final class HttpHeaderNames {
    /**
     * {@code "Accept"}
     */
    public static final CharSequence ACCEPT = io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
    /**
     * {@code "Accept-Charset"}
     */
    public static final CharSequence ACCEPT_CHARSET = io.netty.handler.codec.http.HttpHeaderNames.ACCEPT_CHARSET;
    /**
     * {@code "Accept-Encoding"}
     */
    public static final CharSequence ACCEPT_ENCODING = io.netty.handler.codec.http.HttpHeaderNames.ACCEPT_ENCODING;
    /**
     * {@code "Accept-Language"}
     */
    public static final CharSequence ACCEPT_LANGUAGE = io.netty.handler.codec.http.HttpHeaderNames.ACCEPT_LANGUAGE;
    /**
     * {@code "Accept-Ranges"}
     */
    public static final CharSequence ACCEPT_RANGES = io.netty.handler.codec.http.HttpHeaderNames.ACCEPT_RANGES;
    /**
     * {@code "Accept-Patch"}
     */
    public static final CharSequence ACCEPT_PATCH = io.netty.handler.codec.http.HttpHeaderNames.ACCEPT_PATCH;
    /**
     * {@code "Access-Control-Allow-Credentials"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;
    /**
     * {@code "Access-Control-Allow-Headers"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_HEADERS = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
    /**
     * {@code "Access-Control-Allow-Methods"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_METHODS = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
    /**
     * {@code "Access-Control-Allow-Origin"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
    /**
     * {@code "Access-Control-Expose-Headers"}
     */
    public static final CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS;
    /**
     * {@code "Access-Control-Max-Age"}
     */
    public static final CharSequence ACCESS_CONTROL_MAX_AGE = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_MAX_AGE;
    /**
     * {@code "Access-Control-Request-Headers"}
     */
    public static final CharSequence ACCESS_CONTROL_REQUEST_HEADERS = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS;
    /**
     * {@code "Access-Control-Request-Method"}
     */
    public static final CharSequence ACCESS_CONTROL_REQUEST_METHOD = io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD;
    /**
     * {@code "Age"}
     */
    public static final CharSequence AGE = io.netty.handler.codec.http.HttpHeaderNames.AGE;
    /**
     * {@code "Allow"}
     */
    public static final CharSequence ALLOW = io.netty.handler.codec.http.HttpHeaderNames.ALLOW;
    /**
     * {@code "Authorization"}
     */
    public static final CharSequence AUTHORIZATION = io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
    /**
     * {@code "Cache-Control"}
     */
    public static final CharSequence CACHE_CONTROL = io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
    /**
     * {@code "Connection"}
     */
    public static final CharSequence CONNECTION = io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
    /**
     * {@code "Content-Base"}
     */
    public static final CharSequence CONTENT_BASE = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_BASE;
    /**
     * {@code "Content-Encoding"}
     */
    public static final CharSequence CONTENT_ENCODING = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
    /**
     * {@code "Content-Language"}
     */
    public static final CharSequence CONTENT_LANGUAGE = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LANGUAGE;
    /**
     * {@code "Content-Length"}
     */
    public static final CharSequence CONTENT_LENGTH = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
    /**
     * {@code "Content-Location"}
     */
    public static final CharSequence CONTENT_LOCATION = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LOCATION;
    /**
     * {@code "Content-Transfer-Encoding"}
     */
    public static final CharSequence CONTENT_TRANSFER_ENCODING = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TRANSFER_ENCODING;
    /**
     * {@code "Content-MD5"}
     */
    public static final CharSequence CONTENT_MD5 = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_MD5;
    /**
     * {@code "Content-Range"}
     */
    public static final CharSequence CONTENT_RANGE = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_RANGE;
    /**
     * {@code "Content-Type"}
     */
    public static final CharSequence CONTENT_TYPE = io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
    /**
     * {@code "HttpCookie"}
     */
    public static final CharSequence COOKIE = io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
    /**
     * {@code "Date"}
     */
    public static final CharSequence DATE = io.netty.handler.codec.http.HttpHeaderNames.DATE;
    /**
     * {@code "ETag"}
     */
    public static final CharSequence ETAG = io.netty.handler.codec.http.HttpHeaderNames.ETAG;
    /**
     * {@code "Expect"}
     */
    public static final CharSequence EXPECT = io.netty.handler.codec.http.HttpHeaderNames.EXPECT;
    /**
     * {@code "Expires"}
     */
    public static final CharSequence EXPIRES = io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
    /**
     * {@code "From"}
     */
    public static final CharSequence FROM = io.netty.handler.codec.http.HttpHeaderNames.FROM;
    /**
     * {@code "Host"}
     */
    public static final CharSequence HOST = io.netty.handler.codec.http.HttpHeaderNames.HOST;
    /**
     * {@code "If-Match"}
     */
    public static final CharSequence IF_MATCH = io.netty.handler.codec.http.HttpHeaderNames.IF_MATCH;
    /**
     * {@code "If-Modified-Since"}
     */
    public static final CharSequence IF_MODIFIED_SINCE = io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
    /**
     * {@code "If-None-Match"}
     */
    public static final CharSequence IF_NONE_MATCH = io.netty.handler.codec.http.HttpHeaderNames.IF_NONE_MATCH;
    /**
     * {@code "If-Range"}
     */
    public static final CharSequence IF_RANGE = io.netty.handler.codec.http.HttpHeaderNames.IF_RANGE;
    /**
     * {@code "If-Unmodified-Since"}
     */
    public static final CharSequence IF_UNMODIFIED_SINCE = io.netty.handler.codec.http.HttpHeaderNames.IF_UNMODIFIED_SINCE;
    /**
     * {@code "Last-Modified"}
     */
    public static final CharSequence LAST_MODIFIED = io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
    /**
     * {@code "Location"}
     */
    public static final CharSequence LOCATION = io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
    /**
     * {@code "Max-Forwards"}
     */
    public static final CharSequence MAX_FORWARDS = io.netty.handler.codec.http.HttpHeaderNames.MAX_FORWARDS;
    /**
     * {@code "Origin"}
     */
    public static final CharSequence ORIGIN = io.netty.handler.codec.http.HttpHeaderNames.ORIGIN;
    /**
     * {@code "Pragma"}
     */
    public static final CharSequence PRAGMA = io.netty.handler.codec.http.HttpHeaderNames.PRAGMA;
    /**
     * {@code "Proxy-Authenticate"}
     */
    public static final CharSequence PROXY_AUTHENTICATE = io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHENTICATE;
    /**
     * {@code "Proxy-Authorization"}
     */
    public static final CharSequence PROXY_AUTHORIZATION = io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHORIZATION;
    /**
     * {@code "Range"}
     */
    public static final CharSequence RANGE = io.netty.handler.codec.http.HttpHeaderNames.RANGE;
    /**
     * {@code "Referer"}
     */
    public static final CharSequence REFERER = io.netty.handler.codec.http.HttpHeaderNames.REFERER;
    /**
     * {@code "Retry-After"}
     */
    public static final CharSequence RETRY_AFTER = io.netty.handler.codec.http.HttpHeaderNames.RETRY_AFTER;
    /**
     * {@code "Sec-WebSocket-Key1"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY1 = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_KEY1;
    /**
     * {@code "Sec-WebSocket-Key2"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY2 = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_KEY2;
    /**
     * {@code "Sec-WebSocket-Location"}
     */
    public static final CharSequence SEC_WEBSOCKET_LOCATION = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_LOCATION;
    /**
     * {@code "Sec-WebSocket-Origin"}
     */
    public static final CharSequence SEC_WEBSOCKET_ORIGIN = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_ORIGIN;
    /**
     * {@code "Sec-WebSocket-Protocol"}
     */
    public static final CharSequence SEC_WEBSOCKET_PROTOCOL = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL;
    /**
     * {@code "Sec-WebSocket-Version"}
     */
    public static final CharSequence SEC_WEBSOCKET_VERSION = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_VERSION;
    /**
     * {@code "Sec-WebSocket-Key"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_KEY;
    /**
     * {@code "Sec-WebSocket-Accept"}
     */
    public static final CharSequence SEC_WEBSOCKET_ACCEPT = io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_ACCEPT;
    /**
     * {@code "Server"}
     */
    public static final CharSequence SERVER = io.netty.handler.codec.http.HttpHeaderNames.SERVER;
    /**
     * {@code "Set-HttpCookie"}
     */
    public static final CharSequence SET_COOKIE = io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;
    /**
     * {@code "Set-Cookie2"}
     */
    public static final CharSequence SET_COOKIE2 = io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE2;
    /**
     * {@code "TE"}
     */
    public static final CharSequence TE = io.netty.handler.codec.http.HttpHeaderNames.TE;
    /**
     * {@code "Trailer"}
     */
    public static final CharSequence TRAILER = io.netty.handler.codec.http.HttpHeaderNames.TRAILER;
    /**
     * {@code "Transfer-Encoding"}
     */
    public static final CharSequence TRANSFER_ENCODING = io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
    /**
     * {@code "Upgrade"}
     */
    public static final CharSequence UPGRADE = io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
    /**
     * {@code "User-Agent"}
     */
    public static final CharSequence USER_AGENT = io.netty.handler.codec.http.HttpHeaderNames.USER_AGENT;
    /**
     * {@code "Vary"}
     */
    public static final CharSequence VARY = io.netty.handler.codec.http.HttpHeaderNames.VARY;
    /**
     * {@code "Via"}
     */
    public static final CharSequence VIA = io.netty.handler.codec.http.HttpHeaderNames.VIA;
    /**
     * {@code "Warning"}
     */
    public static final CharSequence WARNING = io.netty.handler.codec.http.HttpHeaderNames.WARNING;
    /**
     * {@code "WebSocket-Location"}
     */
    public static final CharSequence WEBSOCKET_LOCATION = io.netty.handler.codec.http.HttpHeaderNames.WEBSOCKET_LOCATION;
    /**
     * {@code "WebSocket-Origin"}
     */
    public static final CharSequence WEBSOCKET_ORIGIN = io.netty.handler.codec.http.HttpHeaderNames.WEBSOCKET_ORIGIN;
    /**
     * {@code "WebSocket-Protocol"}
     */
    public static final CharSequence WEBSOCKET_PROTOCOL = io.netty.handler.codec.http.HttpHeaderNames.WEBSOCKET_PROTOCOL;
    /**
     * {@code "WWW-Authenticate"}
     */
    public static final CharSequence WWW_AUTHENTICATE = io.netty.handler.codec.http.HttpHeaderNames.WWW_AUTHENTICATE;

    private HttpHeaderNames() {
    }
}
