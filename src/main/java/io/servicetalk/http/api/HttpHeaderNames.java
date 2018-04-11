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
 * Common <a href="https://tools.ietf.org/html/rfc7231#section-5">request header names</a> and
 * <a href="https://tools.ietf.org/html/rfc7231#section-7">response header names</a>.
 */
public final class HttpHeaderNames {
    /**
     * {@code "accept"}
     */
    public static final CharSequence ACCEPT = newCaseInsensitiveAsciiString("accept");
    /**
     * {@code "accept-charset"}
     */
    public static final CharSequence ACCEPT_CHARSET = newCaseInsensitiveAsciiString("accept-charset");
    /**
     * {@code "accept-encoding"}
     */
    public static final CharSequence ACCEPT_ENCODING = newCaseInsensitiveAsciiString("accept-encoding");
    /**
     * {@code "accept-language"}
     */
    public static final CharSequence ACCEPT_LANGUAGE = newCaseInsensitiveAsciiString("accept-language");
    /**
     * {@code "accept-ranges"}
     */
    public static final CharSequence ACCEPT_RANGES = newCaseInsensitiveAsciiString("accept-ranges");
    /**
     * {@code "accept-patch"}
     */
    public static final CharSequence ACCEPT_PATCH = newCaseInsensitiveAsciiString("accept-patch");
    /**
     * {@code "access-control-allow-credentials"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS =
            newCaseInsensitiveAsciiString("access-control-allow-credentials");
    /**
     * {@code "access-control-allow-headers"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_HEADERS =
            newCaseInsensitiveAsciiString("access-control-allow-headers");
    /**
     * {@code "access-control-allow-methods"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_METHODS =
            newCaseInsensitiveAsciiString("access-control-allow-methods");
    /**
     * {@code "access-control-allow-origin"}
     */
    public static final CharSequence ACCESS_CONTROL_ALLOW_ORIGIN =
            newCaseInsensitiveAsciiString("access-control-allow-origin");
    /**
     * {@code "access-control-expose-headers"}
     */
    public static final CharSequence ACCESS_CONTROL_EXPOSE_HEADERS =
            newCaseInsensitiveAsciiString("access-control-expose-headers");
    /**
     * {@code "access-control-max-age"}
     */
    public static final CharSequence ACCESS_CONTROL_MAX_AGE = newCaseInsensitiveAsciiString("access-control-max-age");
    /**
     * {@code "access-control-request-headers"}
     */
    public static final CharSequence ACCESS_CONTROL_REQUEST_HEADERS =
            newCaseInsensitiveAsciiString("access-control-request-headers");
    /**
     * {@code "access-control-request-method"}
     */
    public static final CharSequence ACCESS_CONTROL_REQUEST_METHOD =
            newCaseInsensitiveAsciiString("access-control-request-method");
    /**
     * {@code "age"}
     */
    public static final CharSequence AGE = newCaseInsensitiveAsciiString("age");
    /**
     * {@code "allow"}
     */
    public static final CharSequence ALLOW = newCaseInsensitiveAsciiString("allow");
    /**
     * {@code "authorization"}
     */
    public static final CharSequence AUTHORIZATION = newCaseInsensitiveAsciiString("authorization");
    /**
     * {@code "cache-control"}
     */
    public static final CharSequence CACHE_CONTROL = newCaseInsensitiveAsciiString("cache-control");
    /**
     * {@code "connection"}
     */
    public static final CharSequence CONNECTION = newCaseInsensitiveAsciiString("connection");
    /**
     * {@code "content-base"}
     */
    public static final CharSequence CONTENT_BASE = newCaseInsensitiveAsciiString("content-base");
    /**
     * {@code "content-encoding"}
     */
    public static final CharSequence CONTENT_ENCODING = newCaseInsensitiveAsciiString("content-encoding");
    /**
     * {@code "content-language"}
     */
    public static final CharSequence CONTENT_LANGUAGE = newCaseInsensitiveAsciiString("content-language");
    /**
     * {@code "content-length"}
     */
    public static final CharSequence CONTENT_LENGTH = newCaseInsensitiveAsciiString("content-length");
    /**
     * {@code "content-location"}
     */
    public static final CharSequence CONTENT_LOCATION = newCaseInsensitiveAsciiString("content-location");
    /**
     * {@code "content-transfer-encoding"}
     */
    public static final CharSequence CONTENT_TRANSFER_ENCODING = newCaseInsensitiveAsciiString("content-transfer-encoding");
    /**
     * {@code "content-disposition"}
     */
    public static final CharSequence CONTENT_DISPOSITION = newCaseInsensitiveAsciiString("content-disposition");
    /**
     * {@code "content-md5"}
     */
    public static final CharSequence CONTENT_MD5 = newCaseInsensitiveAsciiString("content-md5");
    /**
     * {@code "content-range"}
     */
    public static final CharSequence CONTENT_RANGE = newCaseInsensitiveAsciiString("content-range");
    /**
     * {@code "content-security-policy"}
     */
    public static final CharSequence CONTENT_SECURITY_POLICY = newCaseInsensitiveAsciiString("content-security-policy");
    /**
     * {@code "content-type"}
     */
    public static final CharSequence CONTENT_TYPE = newCaseInsensitiveAsciiString("content-type");
    /**
     * {@code "cookie"}
     */
    public static final CharSequence COOKIE = newCaseInsensitiveAsciiString("cookie");
    /**
     * {@code "date"}
     */
    public static final CharSequence DATE = newCaseInsensitiveAsciiString("date");
    /**
     * {@code "etag"}
     */
    public static final CharSequence ETAG = newCaseInsensitiveAsciiString("etag");
    /**
     * {@code "expect"}
     */
    public static final CharSequence EXPECT = newCaseInsensitiveAsciiString("expect");
    /**
     * {@code "expires"}
     */
    public static final CharSequence EXPIRES = newCaseInsensitiveAsciiString("expires");
    /**
     * {@code "from"}
     */
    public static final CharSequence FROM = newCaseInsensitiveAsciiString("from");
    /**
     * {@code "host"}
     */
    public static final CharSequence HOST = newCaseInsensitiveAsciiString("host");
    /**
     * {@code "if-match"}
     */
    public static final CharSequence IF_MATCH = newCaseInsensitiveAsciiString("if-match");
    /**
     * {@code "if-modified-since"}
     */
    public static final CharSequence IF_MODIFIED_SINCE = newCaseInsensitiveAsciiString("if-modified-since");
    /**
     * {@code "if-none-match"}
     */
    public static final CharSequence IF_NONE_MATCH = newCaseInsensitiveAsciiString("if-none-match");
    /**
     * {@code "if-range"}
     */
    public static final CharSequence IF_RANGE = newCaseInsensitiveAsciiString("if-range");
    /**
     * {@code "if-unmodified-since"}
     */
    public static final CharSequence IF_UNMODIFIED_SINCE = newCaseInsensitiveAsciiString("if-unmodified-since");
    /**
     * {@code "last-modified"}
     */
    public static final CharSequence LAST_MODIFIED = newCaseInsensitiveAsciiString("last-modified");
    /**
     * {@code "location"}
     */
    public static final CharSequence LOCATION = newCaseInsensitiveAsciiString("location");
    /**
     * {@code "max-forwards"}
     */
    public static final CharSequence MAX_FORWARDS = newCaseInsensitiveAsciiString("max-forwards");
    /**
     * {@code "origin"}
     */
    public static final CharSequence ORIGIN = newCaseInsensitiveAsciiString("origin");
    /**
     * {@code "pragma"}
     */
    public static final CharSequence PRAGMA = newCaseInsensitiveAsciiString("pragma");
    /**
     * {@code "proxy-authenticate"}
     */
    public static final CharSequence PROXY_AUTHENTICATE = newCaseInsensitiveAsciiString("proxy-authenticate");
    /**
     * {@code "proxy-authorization"}
     */
    public static final CharSequence PROXY_AUTHORIZATION = newCaseInsensitiveAsciiString("proxy-authorization");
    /**
     * {@code "range"}
     */
    public static final CharSequence RANGE = newCaseInsensitiveAsciiString("range");
    /**
     * {@code "referer"}
     */
    public static final CharSequence REFERER = newCaseInsensitiveAsciiString("referer");
    /**
     * {@code "retry-after"}
     */
    public static final CharSequence RETRY_AFTER = newCaseInsensitiveAsciiString("retry-after");
    /**
     * {@code "sec-websocket-key1"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY1 = newCaseInsensitiveAsciiString("sec-websocket-key1");
    /**
     * {@code "sec-websocket-key2"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY2 = newCaseInsensitiveAsciiString("sec-websocket-key2");
    /**
     * {@code "sec-websocket-location"}
     */
    public static final CharSequence SEC_WEBSOCKET_LOCATION = newCaseInsensitiveAsciiString("sec-websocket-location");
    /**
     * {@code "sec-websocket-origin"}
     */
    public static final CharSequence SEC_WEBSOCKET_ORIGIN = newCaseInsensitiveAsciiString("sec-websocket-origin");
    /**
     * {@code "sec-websocket-protocol"}
     */
    public static final CharSequence SEC_WEBSOCKET_PROTOCOL = newCaseInsensitiveAsciiString("sec-websocket-protocol");
    /**
     * {@code "sec-websocket-version"}
     */
    public static final CharSequence SEC_WEBSOCKET_VERSION = newCaseInsensitiveAsciiString("sec-websocket-version");
    /**
     * {@code "sec-websocket-key"}
     */
    public static final CharSequence SEC_WEBSOCKET_KEY = newCaseInsensitiveAsciiString("sec-websocket-key");
    /**
     * {@code "sec-websocket-accept"}
     */
    public static final CharSequence SEC_WEBSOCKET_ACCEPT = newCaseInsensitiveAsciiString("sec-websocket-accept");
    /**
     * {@code "sec-websocket-protocol"}
     */
    public static final CharSequence SEC_WEBSOCKET_EXTENSIONS = newCaseInsensitiveAsciiString("sec-websocket-extensions");
    /**
     * {@code "server"}
     */
    public static final CharSequence SERVER = newCaseInsensitiveAsciiString("server");
    /**
     * {@code "set-cookie"}
     */
    public static final CharSequence SET_COOKIE = newCaseInsensitiveAsciiString("set-cookie");
    /**
     * {@code "set-cookie2"}
     */
    public static final CharSequence SET_COOKIE2 = newCaseInsensitiveAsciiString("set-cookie2");
    /**
     * {@code "te"}
     */
    public static final CharSequence TE = newCaseInsensitiveAsciiString("te");
    /**
     * {@code "trailer"}
     */
    public static final CharSequence TRAILER = newCaseInsensitiveAsciiString("trailer");
    /**
     * {@code "transfer-encoding"}
     */
    public static final CharSequence TRANSFER_ENCODING = newCaseInsensitiveAsciiString("transfer-encoding");
    /**
     * {@code "upgrade"}
     */
    public static final CharSequence UPGRADE = newCaseInsensitiveAsciiString("upgrade");
    /**
     * {@code "user-agent"}
     */
    public static final CharSequence USER_AGENT = newCaseInsensitiveAsciiString("user-agent");
    /**
     * {@code "vary"}
     */
    public static final CharSequence VARY = newCaseInsensitiveAsciiString("vary");
    /**
     * {@code "via"}
     */
    public static final CharSequence VIA = newCaseInsensitiveAsciiString("via");
    /**
     * {@code "warning"}
     */
    public static final CharSequence WARNING = newCaseInsensitiveAsciiString("warning");
    /**
     * {@code "websocket-location"}
     */
    public static final CharSequence WEBSOCKET_LOCATION = newCaseInsensitiveAsciiString("websocket-location");
    /**
     * {@code "websocket-origin"}
     */
    public static final CharSequence WEBSOCKET_ORIGIN = newCaseInsensitiveAsciiString("websocket-origin");
    /**
     * {@code "websocket-protocol"}
     */
    public static final CharSequence WEBSOCKET_PROTOCOL = newCaseInsensitiveAsciiString("websocket-protocol");
    /**
     * {@code "www-authenticate"}
     */
    public static final CharSequence WWW_AUTHENTICATE = newCaseInsensitiveAsciiString("www-authenticate");

    private HttpHeaderNames() {
        // No instances
    }
}
