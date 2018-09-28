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

import io.servicetalk.buffer.api.Buffer;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.http.api.DefaultHttpResponseStatus.statusCodeToBuffer;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.toStatusClass;
import static java.util.Objects.requireNonNull;

/**
 * Provides constant instances of {@link HttpResponseStatus}, as well as a mechanism for creating new instances if the
 * existing constants are not sufficient.
 */
public enum HttpResponseStatuses implements HttpResponseStatus {

    /**
     * 100 Continue
     */
    CONTINUE(100, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Continue")),

    /**
     * 101 Switching Protocols
     */
    SWITCHING_PROTOCOLS(101, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Switching Protocols")),

    /**
     * 102 Processing (WebDAV, RFC2518)
     */
    PROCESSING(102, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Processing")),

    /**
     * 200 OK
     */
    OK(200, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("OK")),

    /**
     * 201 Created
     */
    CREATED(201, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Created")),

    /**
     * 202 Accepted
     */
    ACCEPTED(202, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Accepted")),

    /**
     * 203 Non-Authoritative Information (since HTTP/1.1)
     */
    NON_AUTHORITATIVE_INFORMATION(203, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Non-Authoritative Information")),

    /**
     * 204 No Content
     */
    NO_CONTENT(204, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("No Content")),

    /**
     * 205 Reset Content
     */
    RESET_CONTENT(205, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Reset Content")),

    /**
     * 206 Partial Content
     */
    PARTIAL_CONTENT(206, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Partial Content")),

    /**
     * 207 Multi-Status (WebDAV, RFC2518)
     */
    MULTI_STATUS(207, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Multi-Status")),

    /**
     * 300 Multiple Choices
     */
    MULTIPLE_CHOICES(300, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Multiple Choices")),

    /**
     * 301 Moved Permanently
     */
    MOVED_PERMANENTLY(301, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Moved Permanently")),

    /**
     * 302 Found
     */
    FOUND(302, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Found")),

    /**
     * 303 See Other (since HTTP/1.1)
     */
    SEE_OTHER(303, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("See Other")),

    /**
     * 304 Not Modified
     */
    NOT_MODIFIED(304, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Not Modified")),

    /**
     * 305 Use Proxy (since HTTP/1.1)
     */
    USE_PROXY(305, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Use Proxy")),

    /**
     * 307 Temporary Redirect (since HTTP/1.1)
     */
    TEMPORARY_REDIRECT(307, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Temporary Redirect")),

    /**
     * 308 Permanent Redirect (RFC7538)
     */
    PERMANENT_REDIRECT(308, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Permanent Redirect")),

    /**
     * 400 Bad Request
     */
    BAD_REQUEST(400, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Bad Request")),

    /**
     * 401 Unauthorized
     */
    UNAUTHORIZED(401, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Unauthorized")),

    /**
     * 402 Payment Required
     */
    PAYMENT_REQUIRED(402, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Payment Required")),

    /**
     * 403 Forbidden
     */
    FORBIDDEN(403, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Forbidden")),

    /**
     * 404 Not Found
     */
    NOT_FOUND(404, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Not Found")),

    /**
     * 405 Method Not Allowed
     */
    METHOD_NOT_ALLOWED(405, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Method Not Allowed")),

    /**
     * 406 Not Acceptable
     */
    NOT_ACCEPTABLE(406, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Not Acceptable")),

    /**
     * 407 Proxy Authentication Required
     */
    PROXY_AUTHENTICATION_REQUIRED(407, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Proxy Authentication Required")),

    /**
     * 408 Request Timeout
     */
    REQUEST_TIMEOUT(408, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Request Timeout")),

    /**
     * 409 Conflict
     */
    CONFLICT(409, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Conflict")),

    /**
     * 410 Gone
     */
    GONE(410, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Gone")),

    /**
     * 411 Length Required
     */
    LENGTH_REQUIRED(411, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Length Required")),

    /**
     * 412 Precondition Failed
     */
    PRECONDITION_FAILED(412, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Precondition Failed")),

    /**
     * 413 Request Entity Too Large
     */
    REQUEST_ENTITY_TOO_LARGE(413, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Request Entity Too Large")),

    /**
     * 414 Request-URI Too Long
     */
    REQUEST_URI_TOO_LONG(414, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Request-URI Too Long")),

    /**
     * 415 Unsupported Media Type
     */
    UNSUPPORTED_MEDIA_TYPE(415, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Unsupported Media Type")),

    /**
     * 416 Requested Range Not Satisfiable
     */
    REQUESTED_RANGE_NOT_SATISFIABLE(416, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Requested Range Not Satisfiable")),

    /**
     * 417 Expectation Failed
     */
    EXPECTATION_FAILED(417, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Expectation Failed")),

    /**
     * 421 Misdirected Request
     * <p>
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-15#section-9.1.2">421 Status Code</a>
     */
    MISDIRECTED_REQUEST(421, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Misdirected Request")),

    /**
     * 422 Unprocessable Entity (WebDAV, RFC4918)
     */
    UNPROCESSABLE_ENTITY(422, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Unprocessable Entity")),

    /**
     * 423 Locked (WebDAV, RFC4918)
     */
    LOCKED(423, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Locked")),

    /**
     * 424 Failed Dependency (WebDAV, RFC4918)
     */
    FAILED_DEPENDENCY(424, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Failed Dependency")),

    /**
     * 425 Unordered Collection (WebDAV, RFC3648)
     */
    UNORDERED_COLLECTION(425, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Unordered Collection")),

    /**
     * 426 Upgrade Required (RFC2817)
     */
    UPGRADE_REQUIRED(426, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Upgrade Required")),

    /**
     * 428 Precondition Required (RFC6585)
     */
    PRECONDITION_REQUIRED(428, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Precondition Required")),

    /**
     * 429 Too Many Requests (RFC6585)
     */
    TOO_MANY_REQUESTS(429, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Too Many Requests")),

    /**
     * 431 Request Header Fields Too Large (RFC6585)
     */
    REQUEST_HEADER_FIELDS_TOO_LARGE(431, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Request Header Fields Too Large")),

    /**
     * 500 Internal Server Error
     */
    INTERNAL_SERVER_ERROR(500, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Internal Server Error")),

    /**
     * 501 Not Implemented
     */
    NOT_IMPLEMENTED(501, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Not Implemented")),

    /**
     * 502 Bad Gateway
     */
    BAD_GATEWAY(502, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Bad Gateway")),

    /**
     * 503 Service Unavailable
     */
    SERVICE_UNAVAILABLE(503, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Service Unavailable")),

    /**
     * 504 Gateway Timeout
     */
    GATEWAY_TIMEOUT(504, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Gateway Timeout")),

    /**
     * 505 HTTP Version Not Supported
     */
    HTTP_VERSION_NOT_SUPPORTED(505, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("HTTP Version Not Supported")),

    /**
     * 506 Variant Also Negotiates (RFC2295)
     */
    VARIANT_ALSO_NEGOTIATES(506, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Variant Also Negotiates")),

    /**
     * 507 Insufficient Storage (WebDAV, RFC4918)
     */
    INSUFFICIENT_STORAGE(507, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Insufficient Storage")),


    /**
     * 510 Not Extended (RFC2774)
     */
    NOT_EXTENDED(510, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Not Extended")),

    /**
     * 511 Network Authentication Required (RFC6585)
     */
    NETWORK_AUTHENTICATION_REQUIRED(511, PREFER_DIRECT_RO_ALLOCATOR.fromAscii("Network Authentication Required"));

    private final int code;
    private final Buffer reasonPhrase;
    private final Buffer statusCodeBuffer;
    private final StatusClass statusClass;

    HttpResponseStatuses(int code, Buffer reasonPhrase) {
        // No instances.
        this.code = code;
        this.reasonPhrase = requireNonNull(reasonPhrase);
        this.statusClass = toStatusClass(code);
        this.statusCodeBuffer = statusCodeToBuffer(code);
    }

    @Override
    public StatusClass statusClass() {
        return statusClass;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public void writeCodeTo(final Buffer buffer) {
        buffer.writeBytes(statusCodeBuffer, statusCodeBuffer.readerIndex(), statusCodeBuffer.readableBytes());
    }

    @Override
    public void writeReasonPhraseTo(final Buffer buffer) {
        buffer.writeBytes(reasonPhrase, reasonPhrase.readerIndex(), reasonPhrase.readableBytes());
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
    public static HttpResponseStatus getResponseStatus(final int statusCode, final Buffer reasonPhrase) {
        final HttpResponseStatuses responseStatus = valueOf(statusCode);
        if (responseStatus != null &&
                (reasonPhrase.readableBytes() == 0 || responseStatus.reasonPhrase.equals(reasonPhrase))) {
            return responseStatus;
        }
        return new DefaultHttpResponseStatus(statusCode, reasonPhrase);
    }

    @Nullable
    private static HttpResponseStatuses valueOf(final int statusCode) {
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
