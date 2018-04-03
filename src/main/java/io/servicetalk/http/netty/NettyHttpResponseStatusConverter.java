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

import io.servicetalk.http.api.HttpResponseStatus;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;

final class NettyHttpResponseStatusConverter {

    private NettyHttpResponseStatusConverter() {
        // No instances.
    }

    static HttpResponseStatus fromNettyHttpResponseStatus(
            final io.netty.handler.codec.http.HttpResponseStatus nettyStatus) {
        return getResponseStatus(nettyStatus.code(), nettyStatus.reasonPhrase());
    }

    static io.netty.handler.codec.http.HttpResponseStatus toNettyHttpResponseStatus(
            final HttpResponseStatus serviceTalkStatus) {
        final io.netty.handler.codec.http.HttpResponseStatus nettyStatus = valueOf(serviceTalkStatus.getCode());
        if (nettyStatus != null && nettyStatus.reasonPhrase().equals(serviceTalkStatus.getReasonPhrase())) {
            return nettyStatus;
        }
        return new io.netty.handler.codec.http.HttpResponseStatus(serviceTalkStatus.getCode(),
                serviceTalkStatus.getReasonPhrase());
    }

    @Nullable
    private static io.netty.handler.codec.http.HttpResponseStatus valueOf(final int statusCode) {
        switch (statusCode) {
            case 100:
                return io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
            case 101:
                return io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
            case 102:
                return io.netty.handler.codec.http.HttpResponseStatus.PROCESSING;
            case 200:
                return io.netty.handler.codec.http.HttpResponseStatus.OK;
            case 201:
                return io.netty.handler.codec.http.HttpResponseStatus.CREATED;
            case 202:
                return io.netty.handler.codec.http.HttpResponseStatus.ACCEPTED;
            case 203:
                return io.netty.handler.codec.http.HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION;
            case 204:
                return io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
            case 205:
                return io.netty.handler.codec.http.HttpResponseStatus.RESET_CONTENT;
            case 206:
                return io.netty.handler.codec.http.HttpResponseStatus.PARTIAL_CONTENT;
            case 207:
                return io.netty.handler.codec.http.HttpResponseStatus.MULTI_STATUS;
            case 300:
                return io.netty.handler.codec.http.HttpResponseStatus.MULTIPLE_CHOICES;
            case 301:
                return io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY;
            case 302:
                return io.netty.handler.codec.http.HttpResponseStatus.FOUND;
            case 303:
                return io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
            case 304:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
            case 305:
                return io.netty.handler.codec.http.HttpResponseStatus.USE_PROXY;
            case 307:
                return io.netty.handler.codec.http.HttpResponseStatus.TEMPORARY_REDIRECT;
            case 308:
                return io.netty.handler.codec.http.HttpResponseStatus.PERMANENT_REDIRECT;
            case 400:
                return io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
            case 401:
                return io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
            case 402:
                return io.netty.handler.codec.http.HttpResponseStatus.PAYMENT_REQUIRED;
            case 403:
                return io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
            case 404:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
            case 405:
                return io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
            case 406:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_ACCEPTABLE;
            case 407:
                return io.netty.handler.codec.http.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
            case 408:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_TIMEOUT;
            case 409:
                return io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
            case 410:
                return io.netty.handler.codec.http.HttpResponseStatus.GONE;
            case 411:
                return io.netty.handler.codec.http.HttpResponseStatus.LENGTH_REQUIRED;
            case 412:
                return io.netty.handler.codec.http.HttpResponseStatus.PRECONDITION_FAILED;
            case 413:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
            case 414:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_URI_TOO_LONG;
            case 415:
                return io.netty.handler.codec.http.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
            case 416:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
            case 417:
                return io.netty.handler.codec.http.HttpResponseStatus.EXPECTATION_FAILED;
            case 421:
                return io.netty.handler.codec.http.HttpResponseStatus.MISDIRECTED_REQUEST;
            case 422:
                return io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY;
            case 423:
                return io.netty.handler.codec.http.HttpResponseStatus.LOCKED;
            case 424:
                return io.netty.handler.codec.http.HttpResponseStatus.FAILED_DEPENDENCY;
            case 425:
                return io.netty.handler.codec.http.HttpResponseStatus.UNORDERED_COLLECTION;
            case 426:
                return io.netty.handler.codec.http.HttpResponseStatus.UPGRADE_REQUIRED;
            case 428:
                return io.netty.handler.codec.http.HttpResponseStatus.PRECONDITION_REQUIRED;
            case 429:
                return io.netty.handler.codec.http.HttpResponseStatus.TOO_MANY_REQUESTS;
            case 431:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE;
            case 500:
                return io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
            case 501:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
            case 502:
                return io.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
            case 503:
                return io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
            case 504:
                return io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
            case 505:
                return io.netty.handler.codec.http.HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED;
            case 506:
                return io.netty.handler.codec.http.HttpResponseStatus.VARIANT_ALSO_NEGOTIATES;
            case 507:
                return io.netty.handler.codec.http.HttpResponseStatus.INSUFFICIENT_STORAGE;
            case 510:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_EXTENDED;
            case 511:
                return io.netty.handler.codec.http.HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED;
            default:
                return null;
        }
    }
}
