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
import io.servicetalk.http.api.HttpResponseStatuses;

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

    static io.netty.handler.codec.http.HttpResponseStatus toNettyHttpResponseStatus(final HttpResponseStatus stStatus) {
        io.netty.handler.codec.http.HttpResponseStatus nettyStatus = null;
        if (stStatus instanceof HttpResponseStatuses) {
            nettyStatus = valueOf((HttpResponseStatuses) stStatus);
        }
        if (nettyStatus == null) {
            return new io.netty.handler.codec.http.HttpResponseStatus(stStatus.getCode(), stStatus.getReasonPhrase());
        }
        return nettyStatus;
    }

    @Nullable
    private static io.netty.handler.codec.http.HttpResponseStatus valueOf(final HttpResponseStatuses stStatus) {
        switch (stStatus) {
            case CONTINUE:
                return io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
            case SWITCHING_PROTOCOLS:
                return io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
            case PROCESSING:
                return io.netty.handler.codec.http.HttpResponseStatus.PROCESSING;
            case OK:
                return io.netty.handler.codec.http.HttpResponseStatus.OK;
            case CREATED:
                return io.netty.handler.codec.http.HttpResponseStatus.CREATED;
            case ACCEPTED:
                return io.netty.handler.codec.http.HttpResponseStatus.ACCEPTED;
            case NON_AUTHORITATIVE_INFORMATION:
                return io.netty.handler.codec.http.HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION;
            case NO_CONTENT:
                return io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
            case RESET_CONTENT:
                return io.netty.handler.codec.http.HttpResponseStatus.RESET_CONTENT;
            case PARTIAL_CONTENT:
                return io.netty.handler.codec.http.HttpResponseStatus.PARTIAL_CONTENT;
            case MULTI_STATUS:
                return io.netty.handler.codec.http.HttpResponseStatus.MULTI_STATUS;
            case MULTIPLE_CHOICES:
                return io.netty.handler.codec.http.HttpResponseStatus.MULTIPLE_CHOICES;
            case MOVED_PERMANENTLY:
                return io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY;
            case FOUND:
                return io.netty.handler.codec.http.HttpResponseStatus.FOUND;
            case SEE_OTHER:
                return io.netty.handler.codec.http.HttpResponseStatus.SEE_OTHER;
            case NOT_MODIFIED:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
            case USE_PROXY:
                return io.netty.handler.codec.http.HttpResponseStatus.USE_PROXY;
            case TEMPORARY_REDIRECT:
                return io.netty.handler.codec.http.HttpResponseStatus.TEMPORARY_REDIRECT;
            case PERMANENT_REDIRECT:
                return io.netty.handler.codec.http.HttpResponseStatus.PERMANENT_REDIRECT;
            case BAD_REQUEST:
                return io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
            case UNAUTHORIZED:
                return io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
            case PAYMENT_REQUIRED:
                return io.netty.handler.codec.http.HttpResponseStatus.PAYMENT_REQUIRED;
            case FORBIDDEN:
                return io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
            case NOT_FOUND:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED:
                return io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_ACCEPTABLE;
            case PROXY_AUTHENTICATION_REQUIRED:
                return io.netty.handler.codec.http.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
            case REQUEST_TIMEOUT:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_TIMEOUT;
            case CONFLICT:
                return io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
            case GONE:
                return io.netty.handler.codec.http.HttpResponseStatus.GONE;
            case LENGTH_REQUIRED:
                return io.netty.handler.codec.http.HttpResponseStatus.LENGTH_REQUIRED;
            case PRECONDITION_FAILED:
                return io.netty.handler.codec.http.HttpResponseStatus.PRECONDITION_FAILED;
            case REQUEST_ENTITY_TOO_LARGE:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
            case REQUEST_URI_TOO_LONG:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_URI_TOO_LONG;
            case UNSUPPORTED_MEDIA_TYPE:
                return io.netty.handler.codec.http.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
            case REQUESTED_RANGE_NOT_SATISFIABLE:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
            case EXPECTATION_FAILED:
                return io.netty.handler.codec.http.HttpResponseStatus.EXPECTATION_FAILED;
            case MISDIRECTED_REQUEST:
                return io.netty.handler.codec.http.HttpResponseStatus.MISDIRECTED_REQUEST;
            case UNPROCESSABLE_ENTITY:
                return io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY;
            case LOCKED:
                return io.netty.handler.codec.http.HttpResponseStatus.LOCKED;
            case FAILED_DEPENDENCY:
                return io.netty.handler.codec.http.HttpResponseStatus.FAILED_DEPENDENCY;
            case UNORDERED_COLLECTION:
                return io.netty.handler.codec.http.HttpResponseStatus.UNORDERED_COLLECTION;
            case UPGRADE_REQUIRED:
                return io.netty.handler.codec.http.HttpResponseStatus.UPGRADE_REQUIRED;
            case PRECONDITION_REQUIRED:
                return io.netty.handler.codec.http.HttpResponseStatus.PRECONDITION_REQUIRED;
            case TOO_MANY_REQUESTS:
                return io.netty.handler.codec.http.HttpResponseStatus.TOO_MANY_REQUESTS;
            case REQUEST_HEADER_FIELDS_TOO_LARGE:
                return io.netty.handler.codec.http.HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE;
            case INTERNAL_SERVER_ERROR:
                return io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
            case NOT_IMPLEMENTED:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
            case BAD_GATEWAY:
                return io.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
            case SERVICE_UNAVAILABLE:
                return io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
            case GATEWAY_TIMEOUT:
                return io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
            case HTTP_VERSION_NOT_SUPPORTED:
                return io.netty.handler.codec.http.HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED;
            case VARIANT_ALSO_NEGOTIATES:
                return io.netty.handler.codec.http.HttpResponseStatus.VARIANT_ALSO_NEGOTIATES;
            case INSUFFICIENT_STORAGE:
                return io.netty.handler.codec.http.HttpResponseStatus.INSUFFICIENT_STORAGE;
            case NOT_EXTENDED:
                return io.netty.handler.codec.http.HttpResponseStatus.NOT_EXTENDED;
            case NETWORK_AUTHENTICATION_REQUIRED:
                return io.netty.handler.codec.http.HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED;
            default:
                return null;
        }
    }
}
