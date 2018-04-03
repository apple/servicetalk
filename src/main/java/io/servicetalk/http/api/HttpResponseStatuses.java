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

import javax.annotation.Nullable;

/**
 * Provides constant instances of {@link HttpResponseStatus}, as well as a mechanism for creating new instances if the
 * existing constants are not sufficient.
 */
public final class HttpResponseStatuses {
    /**
     * 100 Continue
     */
    public static final HttpResponseStatus CONTINUE = new DefaultHttpResponseStatus(100, "Continue");

    /**
     * 101 Switching Protocols
     */
    public static final HttpResponseStatus SWITCHING_PROTOCOLS =
            new DefaultHttpResponseStatus(101, "Switching Protocols");

    /**
     * 102 Processing (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus PROCESSING = new DefaultHttpResponseStatus(102, "Processing");

    /**
     * 200 OK
     */
    public static final HttpResponseStatus OK = new DefaultHttpResponseStatus(200, "OK");

    /**
     * 201 Created
     */
    public static final HttpResponseStatus CREATED = new DefaultHttpResponseStatus(201, "Created");

    /**
     * 202 Accepted
     */
    public static final HttpResponseStatus ACCEPTED = new DefaultHttpResponseStatus(202, "Accepted");

    /**
     * 203 Non-Authoritative Information (since HTTP/1.1)
     */
    public static final HttpResponseStatus NON_AUTHORITATIVE_INFORMATION =
            new DefaultHttpResponseStatus(203, "Non-Authoritative Information");

    /**
     * 204 No Content
     */
    public static final HttpResponseStatus NO_CONTENT = new DefaultHttpResponseStatus(204, "No Content");

    /**
     * 205 Reset Content
     */
    public static final HttpResponseStatus RESET_CONTENT = new DefaultHttpResponseStatus(205, "Reset Content");

    /**
     * 206 Partial Content
     */
    public static final HttpResponseStatus PARTIAL_CONTENT = new DefaultHttpResponseStatus(206, "Partial Content");

    /**
     * 207 Multi-Status (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus MULTI_STATUS = new DefaultHttpResponseStatus(207, "Multi-Status");

    /**
     * 300 Multiple Choices
     */
    public static final HttpResponseStatus MULTIPLE_CHOICES = new DefaultHttpResponseStatus(300, "Multiple Choices");

    /**
     * 301 Moved Permanently
     */
    public static final HttpResponseStatus MOVED_PERMANENTLY = new DefaultHttpResponseStatus(301, "Moved Permanently");

    /**
     * 302 Found
     */
    public static final HttpResponseStatus FOUND = new DefaultHttpResponseStatus(302, "Found");

    /**
     * 303 See Other (since HTTP/1.1)
     */
    public static final HttpResponseStatus SEE_OTHER = new DefaultHttpResponseStatus(303, "See Other");

    /**
     * 304 Not Modified
     */
    public static final HttpResponseStatus NOT_MODIFIED = new DefaultHttpResponseStatus(304, "Not Modified");

    /**
     * 305 Use Proxy (since HTTP/1.1)
     */
    public static final HttpResponseStatus USE_PROXY = new DefaultHttpResponseStatus(305, "Use Proxy");

    /**
     * 307 Temporary Redirect (since HTTP/1.1)
     */
    public static final HttpResponseStatus TEMPORARY_REDIRECT =
            new DefaultHttpResponseStatus(307, "Temporary Redirect");

    /**
     * 308 Permanent Redirect (RFC7538)
     */
    public static final HttpResponseStatus PERMANENT_REDIRECT =
            new DefaultHttpResponseStatus(308, "Permanent Redirect");

    /**
     * 400 Bad Request
     */
    public static final HttpResponseStatus BAD_REQUEST = new DefaultHttpResponseStatus(400, "Bad Request");

    /**
     * 401 Unauthorized
     */
    public static final HttpResponseStatus UNAUTHORIZED = new DefaultHttpResponseStatus(401, "Unauthorized");

    /**
     * 402 Payment Required
     */
    public static final HttpResponseStatus PAYMENT_REQUIRED = new DefaultHttpResponseStatus(402, "Payment Required");

    /**
     * 403 Forbidden
     */
    public static final HttpResponseStatus FORBIDDEN = new DefaultHttpResponseStatus(403, "Forbidden");

    /**
     * 404 Not Found
     */
    public static final HttpResponseStatus NOT_FOUND = new DefaultHttpResponseStatus(404, "Not Found");

    /**
     * 405 Method Not Allowed
     */
    public static final HttpResponseStatus METHOD_NOT_ALLOWED =
            new DefaultHttpResponseStatus(405, "Method Not Allowed");

    /**
     * 406 Not Acceptable
     */
    public static final HttpResponseStatus NOT_ACCEPTABLE = new DefaultHttpResponseStatus(406, "Not Acceptable");

    /**
     * 407 Proxy Authentication Required
     */
    public static final HttpResponseStatus PROXY_AUTHENTICATION_REQUIRED =
            new DefaultHttpResponseStatus(407, "Proxy Authentication Required");

    /**
     * 408 Request Timeout
     */
    public static final HttpResponseStatus REQUEST_TIMEOUT = new DefaultHttpResponseStatus(408, "Request Timeout");

    /**
     * 409 Conflict
     */
    public static final HttpResponseStatus CONFLICT = new DefaultHttpResponseStatus(409, "Conflict");

    /**
     * 410 Gone
     */
    public static final HttpResponseStatus GONE = new DefaultHttpResponseStatus(410, "Gone");

    /**
     * 411 Length Required
     */
    public static final HttpResponseStatus LENGTH_REQUIRED = new DefaultHttpResponseStatus(411, "Length Required");

    /**
     * 412 Precondition Failed
     */
    public static final HttpResponseStatus PRECONDITION_FAILED =
            new DefaultHttpResponseStatus(412, "Precondition Failed");

    /**
     * 413 Request Entity Too Large
     */
    public static final HttpResponseStatus REQUEST_ENTITY_TOO_LARGE =
            new DefaultHttpResponseStatus(413, "Request Entity Too Large");

    /**
     * 414 Request-URI Too Long
     */
    public static final HttpResponseStatus REQUEST_URI_TOO_LONG =
            new DefaultHttpResponseStatus(414, "Request-URI Too Long");

    /**
     * 415 Unsupported Media Type
     */
    public static final HttpResponseStatus UNSUPPORTED_MEDIA_TYPE =
            new DefaultHttpResponseStatus(415, "Unsupported Media Type");

    /**
     * 416 Requested Range Not Satisfiable
     */
    public static final HttpResponseStatus REQUESTED_RANGE_NOT_SATISFIABLE =
            new DefaultHttpResponseStatus(416, "Requested Range Not Satisfiable");

    /**
     * 417 Expectation Failed
     */
    public static final HttpResponseStatus EXPECTATION_FAILED =
            new DefaultHttpResponseStatus(417, "Expectation Failed");

    /**
     * 421 Misdirected Request
     * <p>
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-15#section-9.1.2">421 Status Code</a>
     */
    public static final HttpResponseStatus MISDIRECTED_REQUEST =
            new DefaultHttpResponseStatus(421, "Misdirected Request");

    /**
     * 422 Unprocessable Entity (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus UNPROCESSABLE_ENTITY =
            new DefaultHttpResponseStatus(422, "Unprocessable Entity");

    /**
     * 423 Locked (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus LOCKED = new DefaultHttpResponseStatus(423, "Locked");

    /**
     * 424 Failed Dependency (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus FAILED_DEPENDENCY = new DefaultHttpResponseStatus(424, "Failed Dependency");

    /**
     * 425 Unordered Collection (WebDAV, RFC3648)
     */
    public static final HttpResponseStatus UNORDERED_COLLECTION =
            new DefaultHttpResponseStatus(425, "Unordered Collection");

    /**
     * 426 Upgrade Required (RFC2817)
     */
    public static final HttpResponseStatus UPGRADE_REQUIRED = new DefaultHttpResponseStatus(426, "Upgrade Required");

    /**
     * 428 Precondition Required (RFC6585)
     */
    public static final HttpResponseStatus PRECONDITION_REQUIRED =
            new DefaultHttpResponseStatus(428, "Precondition Required");

    /**
     * 429 Too Many Requests (RFC6585)
     */
    public static final HttpResponseStatus TOO_MANY_REQUESTS = new DefaultHttpResponseStatus(429, "Too Many Requests");

    /**
     * 431 Request Header Fields Too Large (RFC6585)
     */
    public static final HttpResponseStatus REQUEST_HEADER_FIELDS_TOO_LARGE =
            new DefaultHttpResponseStatus(431, "Request Header Fields Too Large");

    /**
     * 500 Internal Server Error
     */
    public static final HttpResponseStatus INTERNAL_SERVER_ERROR =
            new DefaultHttpResponseStatus(500, "Internal Server Error");

    /**
     * 501 Not Implemented
     */
    public static final HttpResponseStatus NOT_IMPLEMENTED = new DefaultHttpResponseStatus(501, "Not Implemented");

    /**
     * 502 Bad Gateway
     */
    public static final HttpResponseStatus BAD_GATEWAY = new DefaultHttpResponseStatus(502, "Bad Gateway");

    /**
     * 503 Service Unavailable
     */
    public static final HttpResponseStatus SERVICE_UNAVAILABLE =
            new DefaultHttpResponseStatus(503, "Service Unavailable");

    /**
     * 504 Gateway Timeout
     */
    public static final HttpResponseStatus GATEWAY_TIMEOUT = new DefaultHttpResponseStatus(504, "Gateway Timeout");

    /**
     * 505 HTTP Version Not Supported
     */
    public static final HttpResponseStatus HTTP_VERSION_NOT_SUPPORTED =
            new DefaultHttpResponseStatus(505, "HTTP Version Not Supported");

    /**
     * 506 Variant Also Negotiates (RFC2295)
     */
    public static final HttpResponseStatus VARIANT_ALSO_NEGOTIATES =
            new DefaultHttpResponseStatus(506, "Variant Also Negotiates");

    /**
     * 507 Insufficient Storage (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus INSUFFICIENT_STORAGE =
            new DefaultHttpResponseStatus(507, "Insufficient Storage");


    /**
     * 510 Not Extended (RFC2774)
     */
    public static final HttpResponseStatus NOT_EXTENDED = new DefaultHttpResponseStatus(510, "Not Extended");

    /**
     * 511 Network Authentication Required (RFC6585)
     */
    public static final HttpResponseStatus NETWORK_AUTHENTICATION_REQUIRED =
            new DefaultHttpResponseStatus(511, "Network Authentication Required");

    private HttpResponseStatuses() {
        // No instances.
    }

    /**
     * Get a {@link HttpResponseStatus} for the specified {@code statusCode} and {@code reasonPhrase}. If the
     * {@code statusCode} and {@code reasonPhrase} match those of an existing constant, the constant will be returned,
     * otherwise a new instance will be returned.
     *
     * @param statusCode the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a>
     *                   indicating status of the response.
     * @param reasonPhrase the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">reason-phrase</a>
     *                     portion of the response.
     * @return a {@link HttpResponseStatus}.
     */
    public static HttpResponseStatus getResponseStatus(final int statusCode, final String reasonPhrase) {
        final HttpResponseStatus responseStatus = valueOf(statusCode);
        if (responseStatus != null && responseStatus.getReasonPhrase().equals(reasonPhrase)) {
            return responseStatus;
        }
        return new DefaultHttpResponseStatus(statusCode, reasonPhrase);
    }

    @Nullable
    private static HttpResponseStatus valueOf(final int statusCode) {
        switch (statusCode) {
            case 100:
                return CONTINUE;
            case 101:
                return SWITCHING_PROTOCOLS;
            case 102:
                return PROCESSING;
            case 200:
                return OK;
            case 201:
                return CREATED;
            case 202:
                return ACCEPTED;
            case 203:
                return NON_AUTHORITATIVE_INFORMATION;
            case 204:
                return NO_CONTENT;
            case 205:
                return RESET_CONTENT;
            case 206:
                return PARTIAL_CONTENT;
            case 207:
                return MULTI_STATUS;
            case 300:
                return MULTIPLE_CHOICES;
            case 301:
                return MOVED_PERMANENTLY;
            case 302:
                return FOUND;
            case 303:
                return SEE_OTHER;
            case 304:
                return NOT_MODIFIED;
            case 305:
                return USE_PROXY;
            case 307:
                return TEMPORARY_REDIRECT;
            case 308:
                return PERMANENT_REDIRECT;
            case 400:
                return BAD_REQUEST;
            case 401:
                return UNAUTHORIZED;
            case 402:
                return PAYMENT_REQUIRED;
            case 403:
                return FORBIDDEN;
            case 404:
                return NOT_FOUND;
            case 405:
                return METHOD_NOT_ALLOWED;
            case 406:
                return NOT_ACCEPTABLE;
            case 407:
                return PROXY_AUTHENTICATION_REQUIRED;
            case 408:
                return REQUEST_TIMEOUT;
            case 409:
                return CONFLICT;
            case 410:
                return GONE;
            case 411:
                return LENGTH_REQUIRED;
            case 412:
                return PRECONDITION_FAILED;
            case 413:
                return REQUEST_ENTITY_TOO_LARGE;
            case 414:
                return REQUEST_URI_TOO_LONG;
            case 415:
                return UNSUPPORTED_MEDIA_TYPE;
            case 416:
                return REQUESTED_RANGE_NOT_SATISFIABLE;
            case 417:
                return EXPECTATION_FAILED;
            case 421:
                return MISDIRECTED_REQUEST;
            case 422:
                return UNPROCESSABLE_ENTITY;
            case 423:
                return LOCKED;
            case 424:
                return FAILED_DEPENDENCY;
            case 425:
                return UNORDERED_COLLECTION;
            case 426:
                return UPGRADE_REQUIRED;
            case 428:
                return PRECONDITION_REQUIRED;
            case 429:
                return TOO_MANY_REQUESTS;
            case 431:
                return REQUEST_HEADER_FIELDS_TOO_LARGE;
            case 500:
                return INTERNAL_SERVER_ERROR;
            case 501:
                return NOT_IMPLEMENTED;
            case 502:
                return BAD_GATEWAY;
            case 503:
                return SERVICE_UNAVAILABLE;
            case 504:
                return GATEWAY_TIMEOUT;
            case 505:
                return HTTP_VERSION_NOT_SUPPORTED;
            case 506:
                return VARIANT_ALSO_NEGOTIATES;
            case 507:
                return INSUFFICIENT_STORAGE;
            case 510:
                return NOT_EXTENDED;
            case 511:
                return NETWORK_AUTHENTICATION_REQUIRED;
            default:
                return null;
        }
    }
}
