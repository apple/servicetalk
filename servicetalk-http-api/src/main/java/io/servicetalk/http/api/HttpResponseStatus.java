/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.fromStatusCode;
import static java.util.Objects.requireNonNull;

/**
 * <a href="https://tools.ietf.org/html/rfc7231#section-6">Response Status Code</a>.
 */
public final class HttpResponseStatus {

    /**
     * 100 Continue
     */
    public static final HttpResponseStatus CONTINUE = new HttpResponseStatus(100, "Continue");

    /**
     * 101 Switching Protocols
     */
    public static final HttpResponseStatus SWITCHING_PROTOCOLS = new HttpResponseStatus(101, "Switching Protocols");

    /**
     * 102 Processing (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus PROCESSING = new HttpResponseStatus(102, "Processing");

    /**
     * 200 OK
     */
    public static final HttpResponseStatus OK = new HttpResponseStatus(200, "OK");

    /**
     * 201 Created
     */
    public static final HttpResponseStatus CREATED = new HttpResponseStatus(201, "Created");

    /**
     * 202 Accepted
     */
    public static final HttpResponseStatus ACCEPTED = new HttpResponseStatus(202, "Accepted");

    /**
     * 203 Non-Authoritative Information (since HTTP/1.1)
     */
    public static final HttpResponseStatus NON_AUTHORITATIVE_INFORMATION = new HttpResponseStatus(203,
            "Non-Authoritative Information");

    /**
     * 204 No Content
     */
    public static final HttpResponseStatus NO_CONTENT = new HttpResponseStatus(204, "No Content");

    /**
     * 205 Reset Content
     */
    public static final HttpResponseStatus RESET_CONTENT = new HttpResponseStatus(205, "Reset Content");

    /**
     * 206 Partial Content
     */
    public static final HttpResponseStatus PARTIAL_CONTENT = new HttpResponseStatus(206, "Partial Content");

    /**
     * 207 Multi-Status (WebDAV, RFC2518)
     */
    public static final HttpResponseStatus MULTI_STATUS = new HttpResponseStatus(207, "Multi-Status");

    /**
     * 300 Multiple Choices
     */
    public static final HttpResponseStatus MULTIPLE_CHOICES = new HttpResponseStatus(300, "Multiple Choices");

    /**
     * 301 Moved Permanently
     */
    public static final HttpResponseStatus MOVED_PERMANENTLY = new HttpResponseStatus(301, "Moved Permanently");

    /**
     * 302 Found
     */
    public static final HttpResponseStatus FOUND = new HttpResponseStatus(302, "Found");

    /**
     * 303 See Other (since HTTP/1.1)
     */
    public static final HttpResponseStatus SEE_OTHER = new HttpResponseStatus(303, "See Other");

    /**
     * 304 Not Modified
     */
    public static final HttpResponseStatus NOT_MODIFIED = new HttpResponseStatus(304, "Not Modified");

    /**
     * 305 Use Proxy (since HTTP/1.1)
     */
    public static final HttpResponseStatus USE_PROXY = new HttpResponseStatus(305, "Use Proxy");

    /**
     * 307 Temporary Redirect (since HTTP/1.1)
     */
    public static final HttpResponseStatus TEMPORARY_REDIRECT = new HttpResponseStatus(307, "Temporary Redirect");

    /**
     * 308 Permanent Redirect (RFC7538)
     */
    public static final HttpResponseStatus PERMANENT_REDIRECT = new HttpResponseStatus(308, "Permanent Redirect");

    /**
     * 400 Bad Request
     */
    public static final HttpResponseStatus BAD_REQUEST = new HttpResponseStatus(400, "Bad Request");

    /**
     * 401 Unauthorized
     */
    public static final HttpResponseStatus UNAUTHORIZED = new HttpResponseStatus(401, "Unauthorized");

    /**
     * 402 Payment Required
     */
    public static final HttpResponseStatus PAYMENT_REQUIRED = new HttpResponseStatus(402, "Payment Required");

    /**
     * 403 Forbidden
     */
    public static final HttpResponseStatus FORBIDDEN = new HttpResponseStatus(403, "Forbidden");

    /**
     * 404 Not Found
     */
    public static final HttpResponseStatus NOT_FOUND = new HttpResponseStatus(404, "Not Found");

    /**
     * 405 Method Not Allowed
     */
    public static final HttpResponseStatus METHOD_NOT_ALLOWED = new HttpResponseStatus(405, "Method Not Allowed");

    /**
     * 406 Not Acceptable
     */
    public static final HttpResponseStatus NOT_ACCEPTABLE = new HttpResponseStatus(406, "Not Acceptable");

    /**
     * 407 Proxy Authentication Required
     */
    public static final HttpResponseStatus PROXY_AUTHENTICATION_REQUIRED = new HttpResponseStatus(407,
            "Proxy Authentication Required");

    /**
     * 408 Request Timeout
     */
    public static final HttpResponseStatus REQUEST_TIMEOUT = new HttpResponseStatus(408, "Request Timeout");

    /**
     * 409 Conflict
     */
    public static final HttpResponseStatus CONFLICT = new HttpResponseStatus(409, "Conflict");

    /**
     * 410 Gone
     */
    public static final HttpResponseStatus GONE = new HttpResponseStatus(410, "Gone");

    /**
     * 411 Length Required
     */
    public static final HttpResponseStatus LENGTH_REQUIRED = new HttpResponseStatus(411, "Length Required");

    /**
     * 412 Precondition Failed
     */
    public static final HttpResponseStatus PRECONDITION_FAILED = new HttpResponseStatus(412, "Precondition Failed");

    /**
     * 413 Request Entity Too Large
     */
    public static final HttpResponseStatus REQUEST_ENTITY_TOO_LARGE = new HttpResponseStatus(413,
            "Request Entity Too Large");

    /**
     * 414 Request-URI Too Long
     */
    public static final HttpResponseStatus REQUEST_URI_TOO_LONG = new HttpResponseStatus(414, "Request-URI Too Long");

    /**
     * 415 Unsupported Media Type
     */
    public static final HttpResponseStatus UNSUPPORTED_MEDIA_TYPE = new HttpResponseStatus(415,
            "Unsupported Media Type");

    /**
     * 416 Requested Range Not Satisfiable
     */
    public static final HttpResponseStatus REQUESTED_RANGE_NOT_SATISFIABLE = new HttpResponseStatus(416,
            "Requested Range Not Satisfiable");

    /**
     * 417 Expectation Failed
     */
    public static final HttpResponseStatus EXPECTATION_FAILED = new HttpResponseStatus(417, "Expectation Failed");

    /**
     * 421 Misdirected Request
     * <p>
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-15#section-9.1.2">421 Status Code</a>
     */
    public static final HttpResponseStatus MISDIRECTED_REQUEST = new HttpResponseStatus(421, "Misdirected Request");

    /**
     * 422 Unprocessable Entity (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus UNPROCESSABLE_ENTITY = new HttpResponseStatus(422, "Unprocessable Entity");

    /**
     * 423 Locked (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus LOCKED = new HttpResponseStatus(423, "Locked");

    /**
     * 424 Failed Dependency (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus FAILED_DEPENDENCY = new HttpResponseStatus(424, "Failed Dependency");

    /**
     * 425 Unordered Collection (WebDAV, RFC3648)
     */
    public static final HttpResponseStatus UNORDERED_COLLECTION = new HttpResponseStatus(425, "Unordered Collection");

    /**
     * 426 Upgrade Required (RFC2817)
     */
    public static final HttpResponseStatus UPGRADE_REQUIRED = new HttpResponseStatus(426, "Upgrade Required");

    /**
     * 428 Precondition Required (RFC6585)
     */
    public static final HttpResponseStatus PRECONDITION_REQUIRED = new HttpResponseStatus(428,
            "Precondition Required");

    /**
     * 429 Too Many Requests (RFC6585)
     */
    public static final HttpResponseStatus TOO_MANY_REQUESTS = new HttpResponseStatus(429, "Too Many Requests");

    /**
     * 431 Request Header Fields Too Large (RFC6585)
     */
    public static final HttpResponseStatus REQUEST_HEADER_FIELDS_TOO_LARGE = new HttpResponseStatus(431,
            "Request Header Fields Too Large");

    /**
     * 500 Internal Server Error
     */
    public static final HttpResponseStatus INTERNAL_SERVER_ERROR = new HttpResponseStatus(500,
            "Internal Server Error");

    /**
     * 501 Not Implemented
     */
    public static final HttpResponseStatus NOT_IMPLEMENTED = new HttpResponseStatus(501, "Not Implemented");

    /**
     * 502 Bad Gateway
     */
    public static final HttpResponseStatus BAD_GATEWAY = new HttpResponseStatus(502, "Bad Gateway");

    /**
     * 503 Service Unavailable
     */
    public static final HttpResponseStatus SERVICE_UNAVAILABLE = new HttpResponseStatus(503, "Service Unavailable");

    /**
     * 504 Gateway Timeout
     */
    public static final HttpResponseStatus GATEWAY_TIMEOUT = new HttpResponseStatus(504, "Gateway Timeout");

    /**
     * 505 HTTP Version Not Supported
     */
    public static final HttpResponseStatus HTTP_VERSION_NOT_SUPPORTED = new HttpResponseStatus(505,
            "HTTP Version Not Supported");

    /**
     * 506 Variant Also Negotiates (RFC2295)
     */
    public static final HttpResponseStatus VARIANT_ALSO_NEGOTIATES = new HttpResponseStatus(506,
            "Variant Also Negotiates");

    /**
     * 507 Insufficient Storage (WebDAV, RFC4918)
     */
    public static final HttpResponseStatus INSUFFICIENT_STORAGE = new HttpResponseStatus(507, "Insufficient Storage");

    /**
     * 510 Not Extended (RFC2774)
     */
    public static final HttpResponseStatus NOT_EXTENDED = new HttpResponseStatus(510, "Not Extended");

    /**
     * 511 Network Authentication Required (RFC6585)
     */
    public static final HttpResponseStatus NETWORK_AUTHENTICATION_REQUIRED = new HttpResponseStatus(511,
            "Network Authentication Required");

    private final int statusCode;
    private final String reasonPhrase;
    private final StatusClass statusClass;
    private final Buffer statusCodeBuffer;
    private final Buffer reasonPhraseBuffer;

    private HttpResponseStatus(final int statusCode, final String reasonPhrase) {
        this.statusCode = statusCode;
        this.reasonPhrase = requireNonNull(reasonPhrase);
        this.statusClass = fromStatusCode(statusCode);
        this.statusCodeBuffer = PREFER_HEAP_RO_ALLOCATOR.fromAscii(Integer.toString(statusCode));
        this.reasonPhraseBuffer = reasonPhrase.isEmpty() ?
                EMPTY_BUFFER : PREFER_HEAP_RO_ALLOCATOR.fromAscii(reasonPhrase);
    }

    /**
     * Returns an {@link HttpResponseStatus} for the specified {@code statusCode} and {@code reasonPhrase}.
     * Generally, the constants in {@link HttpResponseStatus} should be used.
     *
     * @param statusCode the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a>
     * indicating status of the response
     * @param reasonPhrase the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">reason-phrase</a>
     * portion of the response
     * @return an {@link HttpResponseStatus}
     * @throws IllegalArgumentException if {@code statusCode} is not a 3-digit integer
     */
    public static HttpResponseStatus of(final int statusCode, final String reasonPhrase) {
        final HttpResponseStatus cached = valueOf(statusCode);
        if (cached != null && (reasonPhrase.isEmpty() || cached.reasonPhrase.equals(reasonPhrase))) {
            return cached;
        }
        return new HttpResponseStatus(statusCode, reasonPhrase);
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

    /**
     * Get the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a> indicating status of
     * the response.
     *
     * @return the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a> indicating status
     * of the response
     */
    public int code() {
        return statusCode;
    }

    /**
     * Write the equivalent of {@link #code()} to a {@link Buffer}.
     *
     * @param buffer The {@link Buffer} to write to
     */
    public void writeCodeTo(final Buffer buffer) {
        buffer.writeBytes(statusCodeBuffer, statusCodeBuffer.readerIndex(), statusCodeBuffer.readableBytes());
    }

    /**
     * Write the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">reason-phrase</a> portion of the
     * response to a {@link Buffer}.
     * <pre>
     *     The reason-phrase element exists for the sole purpose of providing a
     *     textual description associated with the numeric status code, mostly
     *     out of deference to earlier Internet application protocols that were
     *     more frequently used with interactive text clients.  A client SHOULD
     *     ignore the reason-phrase content.
     * </pre>
     *
     * @param buffer The {@link Buffer} to write to
     */
    public void writeReasonPhraseTo(final Buffer buffer) {
        buffer.writeBytes(reasonPhraseBuffer, reasonPhraseBuffer.readerIndex(), reasonPhraseBuffer.readableBytes());
    }

    /**
     * Get the {@link StatusClass} for this {@link HttpResponseStatus}.
     *
     * @return the {@link StatusClass} for this {@link HttpResponseStatus}
     */
    public StatusClass statusClass() {
        return statusClass;
    }

    @Override
    public String toString() {
        return reasonPhrase.isEmpty() ? Integer.toString(statusCode) : statusCode + " " + reasonPhrase;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpResponseStatus)) {
            return false;
        }

        final HttpResponseStatus that = (HttpResponseStatus) o;
        /*
         * - statusCodeBuffer is ignored for equals/hashCode because it is inherited from statusCode and the
         *   relationship is idempotent
         *
         * - reasonPhrase is ignored for equals/hashCode because the RFC says:
         *   A client SHOULD ignore the reason-phrase content.
         * https://tools.ietf.org/html/rfc7230#section-3.1.2
         *
         * - statusClass is ignored for equals/hashCode because it is inherited from statusCode and the relationship is
         *   idempotent
         */
        return statusCode == that.code();
    }

    @Override
    public int hashCode() {
        return 31 * statusCode;
    }

    /**
     * The class of <a href="https://tools.ietf.org/html/rfc7231#section-6">response status codes</a>.
     */
    public enum StatusClass {
        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.2">Informational 1xx</a>.
         */
        INFORMATIONAL_1XX(100, 199),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.3">Successful 2xx</a>.
         */
        SUCCESSFUL_2XX(200, 299),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.4">Redirection 3xx</a>.
         */
        REDIRECTION_3XX(300, 399),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.5">Client Error 4xx</a>.
         */
        CLIENT_ERROR_4XX(400, 499),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.6">Server Error 5xx</a>.
         */
        SERVER_ERROR_5XX(500, 599),

        /**
         * Unknown. 3-digit status codes outside of the defined range of
         * <a href="https://tools.ietf.org/html/rfc7231#section-6">response status codes</a>.
         */
        UNKNOWN(600, 999);

        private final int minStatus;
        private final int maxStatus;

        StatusClass(final int minStatus, final int maxStatus) {
            this.minStatus = minStatus;
            this.maxStatus = maxStatus;
        }

        /**
         * Determine if {@code code} falls into this {@link StatusClass}.
         *
         * @param statusCode the status code to test
         * @return {@code true} if and only if the specified HTTP status code falls into this class
         */
        public boolean contains(final int statusCode) {
            return minStatus <= statusCode && statusCode <= maxStatus;
        }

        /**
         * Determine if {@code status} code falls into this {@link StatusClass}.
         *
         * @param status the status to test
         * @return {@code true} if and only if the specified HTTP status code falls into this class
         */
        public boolean contains(final HttpResponseStatus status) {
            return contains(status.code());
        }

        /**
         * Determines the {@link StatusClass} from the {@code statusCode}.
         *
         * @param statusCode the status code to use for determining the {@link StatusClass}
         * @return one of the {@link StatusClass} enum values
         * @throws IllegalArgumentException if {@code statusCode} is not a 3-digit integer
         */
        public static StatusClass fromStatusCode(final int statusCode) {
            if (statusCode < 100 || statusCode > 999) {
                throw new IllegalArgumentException("Illegal status code: " + statusCode + ", expected [100-999]");
            }

            if (INFORMATIONAL_1XX.contains(statusCode)) {
                return INFORMATIONAL_1XX;
            }
            if (SUCCESSFUL_2XX.contains(statusCode)) {
                return SUCCESSFUL_2XX;
            }
            if (REDIRECTION_3XX.contains(statusCode)) {
                return REDIRECTION_3XX;
            }
            if (CLIENT_ERROR_4XX.contains(statusCode)) {
                return CLIENT_ERROR_4XX;
            }
            if (SERVER_ERROR_5XX.contains(statusCode)) {
                return SERVER_ERROR_5XX;
            }
            return UNKNOWN;
        }
    }
}
