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

import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.CONFLICT;
import static io.servicetalk.http.api.HttpResponseStatuses.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatuses.CREATED;
import static io.servicetalk.http.api.HttpResponseStatuses.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatuses.FAILED_DEPENDENCY;
import static io.servicetalk.http.api.HttpResponseStatuses.FORBIDDEN;
import static io.servicetalk.http.api.HttpResponseStatuses.FOUND;
import static io.servicetalk.http.api.HttpResponseStatuses.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatuses.GONE;
import static io.servicetalk.http.api.HttpResponseStatuses.HTTP_VERSION_NOT_SUPPORTED;
import static io.servicetalk.http.api.HttpResponseStatuses.INSUFFICIENT_STORAGE;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.LENGTH_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.LOCKED;
import static io.servicetalk.http.api.HttpResponseStatuses.METHOD_NOT_ALLOWED;
import static io.servicetalk.http.api.HttpResponseStatuses.MISDIRECTED_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.MOVED_PERMANENTLY;
import static io.servicetalk.http.api.HttpResponseStatuses.MULTIPLE_CHOICES;
import static io.servicetalk.http.api.HttpResponseStatuses.MULTI_STATUS;
import static io.servicetalk.http.api.HttpResponseStatuses.NETWORK_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.NON_AUTHORITATIVE_INFORMATION;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_ACCEPTABLE;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_EXTENDED;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_IMPLEMENTED;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.PARTIAL_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.PAYMENT_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatuses.PRECONDITION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatuses.PRECONDITION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.PROCESSING;
import static io.servicetalk.http.api.HttpResponseStatuses.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.REQUESTED_RANGE_NOT_SATISFIABLE;
import static io.servicetalk.http.api.HttpResponseStatuses.REQUEST_ENTITY_TOO_LARGE;
import static io.servicetalk.http.api.HttpResponseStatuses.REQUEST_HEADER_FIELDS_TOO_LARGE;
import static io.servicetalk.http.api.HttpResponseStatuses.REQUEST_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatuses.REQUEST_URI_TOO_LONG;
import static io.servicetalk.http.api.HttpResponseStatuses.RESET_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.SEE_OTHER;
import static io.servicetalk.http.api.HttpResponseStatuses.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.api.HttpResponseStatuses.SWITCHING_PROTOCOLS;
import static io.servicetalk.http.api.HttpResponseStatuses.TEMPORARY_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatuses.TOO_MANY_REQUESTS;
import static io.servicetalk.http.api.HttpResponseStatuses.UNAUTHORIZED;
import static io.servicetalk.http.api.HttpResponseStatuses.UNORDERED_COLLECTION;
import static io.servicetalk.http.api.HttpResponseStatuses.UNPROCESSABLE_ENTITY;
import static io.servicetalk.http.api.HttpResponseStatuses.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpResponseStatuses.UPGRADE_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatuses.USE_PROXY;
import static io.servicetalk.http.api.HttpResponseStatuses.VARIANT_ALSO_NEGOTIATES;

/**
 * A factory for creating {@link BlockingStreamingHttpResponse}s.
 */
public interface BlockingStreamingHttpResponseFactory {
    /**
     * Create a new {@link StreamingHttpResponse} object.
     * @param status The {@link HttpResponseStatus}.
     * @return a new {@link StreamingHttpResponse} object.
     */
    BlockingStreamingHttpResponse newResponse(HttpResponseStatus status);

    /**
     * Create a new {@link HttpResponseStatuses#CONTINUE} response.
     * @return a new {@link HttpResponseStatuses#CONTINUE} response.
     */
    default BlockingStreamingHttpResponse continueResponse() {
        return newResponse(CONTINUE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#SWITCHING_PROTOCOLS} response.
     * @return a new {@link HttpResponseStatuses#SWITCHING_PROTOCOLS} response.
     */
    default BlockingStreamingHttpResponse switchingProtocols() {
        return newResponse(SWITCHING_PROTOCOLS);
    }

    /**
     * Create a new {@link HttpResponseStatuses#PROCESSING} response.
     * @return a new {@link HttpResponseStatuses#PROCESSING} response.
     */
    default BlockingStreamingHttpResponse processing() {
        return newResponse(PROCESSING);
    }

    /**
     * Create a new {@link HttpResponseStatuses#OK} response.
     * @return a new {@link HttpResponseStatuses#OK} response.
     */
    default BlockingStreamingHttpResponse ok() {
        return newResponse(OK);
    }

    /**
     * Create a new {@link HttpResponseStatuses#CREATED} response.
     * @return a new {@link HttpResponseStatuses#CREATED} response.
     */
    default BlockingStreamingHttpResponse created() {
        return newResponse(CREATED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#ACCEPTED} response.
     * @return a new {@link HttpResponseStatuses#ACCEPTED} response.
     */
    default BlockingStreamingHttpResponse accepted() {
        return newResponse(ACCEPTED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NON_AUTHORITATIVE_INFORMATION} response.
     * @return a new {@link HttpResponseStatuses#NON_AUTHORITATIVE_INFORMATION} response.
     */
    default BlockingStreamingHttpResponse nonAuthoritativeInformation() {
        return newResponse(NON_AUTHORITATIVE_INFORMATION);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NO_CONTENT} response.
     * @return a new {@link HttpResponseStatuses#NO_CONTENT} response.
     */
    default BlockingStreamingHttpResponse noContent() {
        return newResponse(NO_CONTENT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#RESET_CONTENT} response.
     * @return a new {@link HttpResponseStatuses#RESET_CONTENT} response.
     */
    default BlockingStreamingHttpResponse resetContent() {
        return newResponse(RESET_CONTENT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#PARTIAL_CONTENT} response.
     * @return a new {@link HttpResponseStatuses#PARTIAL_CONTENT} response.
     */
    default BlockingStreamingHttpResponse partialContent() {
        return newResponse(PARTIAL_CONTENT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#MULTI_STATUS} response.
     * @return a new {@link HttpResponseStatuses#MULTI_STATUS} response.
     */
    default BlockingStreamingHttpResponse multiStatus() {
        return newResponse(MULTI_STATUS);
    }

    /**
     * Create a new {@link HttpResponseStatuses#MULTIPLE_CHOICES} response.
     * @return a new {@link HttpResponseStatuses#MULTIPLE_CHOICES} response.
     */
    default BlockingStreamingHttpResponse multipleChoices() {
        return newResponse(MULTIPLE_CHOICES);
    }

    /**
     * Create a new {@link HttpResponseStatuses#MOVED_PERMANENTLY} response.
     * @return a new {@link HttpResponseStatuses#MOVED_PERMANENTLY} response.
     */
    default BlockingStreamingHttpResponse movedPermanently() {
        return newResponse(MOVED_PERMANENTLY);
    }

    /**
     * Create a new {@link HttpResponseStatuses#FOUND} response.
     * @return a new {@link HttpResponseStatuses#FOUND} response.
     */
    default BlockingStreamingHttpResponse found() {
        return newResponse(FOUND);
    }

    /**
     * Create a new {@link HttpResponseStatuses#SEE_OTHER} response.
     * @return a new {@link HttpResponseStatuses#SEE_OTHER} response.
     */
    default BlockingStreamingHttpResponse seeOther() {
        return newResponse(SEE_OTHER);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NOT_MODIFIED} response.
     * @return a new {@link HttpResponseStatuses#NOT_MODIFIED} response.
     */
    default BlockingStreamingHttpResponse notModified() {
        return newResponse(NOT_MODIFIED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#USE_PROXY} response.
     * @return a new {@link HttpResponseStatuses#USE_PROXY} response.
     */
    default BlockingStreamingHttpResponse useProxy() {
        return newResponse(USE_PROXY);
    }

    /**
     * Create a new {@link HttpResponseStatuses#TEMPORARY_REDIRECT} response.
     * @return a new {@link HttpResponseStatuses#TEMPORARY_REDIRECT} response.
     */
    default BlockingStreamingHttpResponse temporaryRedirect() {
        return newResponse(TEMPORARY_REDIRECT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#PERMANENT_REDIRECT} response.
     * @return a new {@link HttpResponseStatuses#PERMANENT_REDIRECT} response.
     */
    default BlockingStreamingHttpResponse permanentRedirect() {
        return newResponse(PERMANENT_REDIRECT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#BAD_REQUEST} response.
     * @return a new {@link HttpResponseStatuses#BAD_REQUEST} response.
     */
    default BlockingStreamingHttpResponse badRequest() {
        return newResponse(BAD_REQUEST);
    }

    /**
     * Create a new {@link HttpResponseStatuses#UNAUTHORIZED} response.
     * @return a new {@link HttpResponseStatuses#UNAUTHORIZED} response.
     */
    default BlockingStreamingHttpResponse unauthorized() {
        return newResponse(UNAUTHORIZED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#PAYMENT_REQUIRED} response.
     * @return a new {@link HttpResponseStatuses#PAYMENT_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse paymentRequired() {
        return newResponse(PAYMENT_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#FORBIDDEN} response.
     * @return a new {@link HttpResponseStatuses#FORBIDDEN} response.
     */
    default BlockingStreamingHttpResponse forbidden() {
        return newResponse(FORBIDDEN);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NOT_FOUND} response.
     * @return a new {@link HttpResponseStatuses#NOT_FOUND} response.
     */
    default BlockingStreamingHttpResponse notFound() {
        return newResponse(NOT_FOUND);
    }

    /**
     * Create a new {@link HttpResponseStatuses#METHOD_NOT_ALLOWED} response.
     * @return a new {@link HttpResponseStatuses#METHOD_NOT_ALLOWED} response.
     */
    default BlockingStreamingHttpResponse methodNotAllowed() {
        return newResponse(METHOD_NOT_ALLOWED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NOT_ACCEPTABLE} response.
     * @return a new {@link HttpResponseStatuses#NOT_ACCEPTABLE} response.
     */
    default BlockingStreamingHttpResponse notAcceptable() {
        return newResponse(NOT_ACCEPTABLE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#PROXY_AUTHENTICATION_REQUIRED} response.
     * @return a new {@link HttpResponseStatuses#PROXY_AUTHENTICATION_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse proxyAuthenticationRequired() {
        return newResponse(PROXY_AUTHENTICATION_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#REQUEST_TIMEOUT} response.
     * @return a new {@link HttpResponseStatuses#REQUEST_TIMEOUT} response.
     */
    default BlockingStreamingHttpResponse requestTimeout() {
        return newResponse(REQUEST_TIMEOUT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#CONFLICT} response.
     * @return a new {@link HttpResponseStatuses#CONFLICT} response.
     */
    default BlockingStreamingHttpResponse conflict() {
        return newResponse(CONFLICT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#GONE} response.
     * @return a new {@link HttpResponseStatuses#GONE} response.
     */
    default BlockingStreamingHttpResponse gone() {
        return newResponse(GONE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#LENGTH_REQUIRED} response.
     * @return a new {@link HttpResponseStatuses#LENGTH_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse lengthRequired() {
        return newResponse(LENGTH_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#PRECONDITION_FAILED} response.
     * @return a new {@link HttpResponseStatuses#PRECONDITION_FAILED} response.
     */
    default BlockingStreamingHttpResponse preconditionFailed() {
        return newResponse(PRECONDITION_FAILED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#REQUEST_ENTITY_TOO_LARGE} response.
     * @return a new {@link HttpResponseStatuses#REQUEST_ENTITY_TOO_LARGE} response.
     */
    default BlockingStreamingHttpResponse requestEntityTooLarge() {
        return newResponse(REQUEST_ENTITY_TOO_LARGE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#REQUEST_URI_TOO_LONG} response.
     * @return a new {@link HttpResponseStatuses#REQUEST_URI_TOO_LONG} response.
     */
    default BlockingStreamingHttpResponse requestUriTooLong() {
        return newResponse(REQUEST_URI_TOO_LONG);
    }

    /**
     * Create a new {@link HttpResponseStatuses#UNSUPPORTED_MEDIA_TYPE} response.
     * @return a new {@link HttpResponseStatuses#UNSUPPORTED_MEDIA_TYPE} response.
     */
    default BlockingStreamingHttpResponse unsupportedMediaType() {
        return newResponse(UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#REQUESTED_RANGE_NOT_SATISFIABLE} response.
     * @return a new {@link HttpResponseStatuses#REQUESTED_RANGE_NOT_SATISFIABLE} response.
     */
    default BlockingStreamingHttpResponse requestedRangeNotSatisfiable() {
        return newResponse(REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#EXPECTATION_FAILED} response.
     * @return a new {@link HttpResponseStatuses#EXPECTATION_FAILED} response.
     */
    default BlockingStreamingHttpResponse expectationFailed() {
        return newResponse(EXPECTATION_FAILED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#MISDIRECTED_REQUEST} response.
     * @return a new {@link HttpResponseStatuses#MISDIRECTED_REQUEST} response.
     */
    default BlockingStreamingHttpResponse misdirectedRequest() {
        return newResponse(MISDIRECTED_REQUEST);
    }

    /**
     * Create a new {@link HttpResponseStatuses#UNPROCESSABLE_ENTITY} response.
     * @return a new {@link HttpResponseStatuses#UNPROCESSABLE_ENTITY} response.
     */
    default BlockingStreamingHttpResponse unprocessableEntity() {
        return newResponse(UNPROCESSABLE_ENTITY);
    }

    /**
     * Create a new {@link HttpResponseStatuses#LOCKED} response.
     * @return a new {@link HttpResponseStatuses#LOCKED} response.
     */
    default BlockingStreamingHttpResponse locked() {
        return newResponse(LOCKED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#FAILED_DEPENDENCY} response.
     * @return a new {@link HttpResponseStatuses#FAILED_DEPENDENCY} response.
     */
    default BlockingStreamingHttpResponse failedDependency() {
        return newResponse(FAILED_DEPENDENCY);
    }

    /**
     * Create a new {@link HttpResponseStatuses#UNORDERED_COLLECTION} response.
     * @return a new {@link HttpResponseStatuses#UNORDERED_COLLECTION} response.
     */
    default BlockingStreamingHttpResponse unorderedCollection() {
        return newResponse(UNORDERED_COLLECTION);
    }

    /**
     * Create a new {@link HttpResponseStatuses#UPGRADE_REQUIRED} response.
     * @return a new {@link HttpResponseStatuses#UPGRADE_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse upgradeRequired() {
        return newResponse(UPGRADE_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#PRECONDITION_REQUIRED} response.
     * @return a new {@link HttpResponseStatuses#PRECONDITION_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse preconditionRequired() {
        return newResponse(PRECONDITION_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#TOO_MANY_REQUESTS} response.
     * @return a new {@link HttpResponseStatuses#TOO_MANY_REQUESTS} response.
     */
    default BlockingStreamingHttpResponse tooManyRequests() {
        return newResponse(TOO_MANY_REQUESTS);
    }

    /**
     * Create a new {@link HttpResponseStatuses#REQUEST_HEADER_FIELDS_TOO_LARGE} response.
     * @return a new {@link HttpResponseStatuses#REQUEST_HEADER_FIELDS_TOO_LARGE} response.
     */
    default BlockingStreamingHttpResponse requestHeaderFieldsTooLarge() {
        return newResponse(REQUEST_HEADER_FIELDS_TOO_LARGE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#INTERNAL_SERVER_ERROR} response.
     * @return a new {@link HttpResponseStatuses#INTERNAL_SERVER_ERROR} response.
     */
    default BlockingStreamingHttpResponse internalServerError() {
        return newResponse(INTERNAL_SERVER_ERROR);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NOT_IMPLEMENTED} response.
     * @return a new {@link HttpResponseStatuses#NOT_IMPLEMENTED} response.
     */
    default BlockingStreamingHttpResponse notImplemented() {
        return newResponse(NOT_IMPLEMENTED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#BAD_GATEWAY} response.
     * @return a new {@link HttpResponseStatuses#BAD_GATEWAY} response.
     */
    default BlockingStreamingHttpResponse badGateway() {
        return newResponse(BAD_GATEWAY);
    }

    /**
     * Create a new {@link HttpResponseStatuses#SERVICE_UNAVAILABLE} response.
     * @return a new {@link HttpResponseStatuses#SERVICE_UNAVAILABLE} response.
     */
    default BlockingStreamingHttpResponse serviceUnavailable() {
        return newResponse(SERVICE_UNAVAILABLE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#GATEWAY_TIMEOUT} response.
     * @return a new {@link HttpResponseStatuses#GATEWAY_TIMEOUT} response.
     */
    default BlockingStreamingHttpResponse gatewayTimeout() {
        return newResponse(GATEWAY_TIMEOUT);
    }

    /**
     * Create a new {@link HttpResponseStatuses#HTTP_VERSION_NOT_SUPPORTED} response.
     * @return a new {@link HttpResponseStatuses#HTTP_VERSION_NOT_SUPPORTED} response.
     */
    default BlockingStreamingHttpResponse httpVersionNotSupported() {
        return newResponse(HTTP_VERSION_NOT_SUPPORTED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#VARIANT_ALSO_NEGOTIATES} response.
     * @return a new {@link HttpResponseStatuses#VARIANT_ALSO_NEGOTIATES} response.
     */
    default BlockingStreamingHttpResponse variantAlsoNegotiates() {
        return newResponse(VARIANT_ALSO_NEGOTIATES);
    }

    /**
     * Create a new {@link HttpResponseStatuses#INSUFFICIENT_STORAGE} response.
     * @return a new {@link HttpResponseStatuses#INSUFFICIENT_STORAGE} response.
     */
    default BlockingStreamingHttpResponse insufficientStorage() {
        return newResponse(INSUFFICIENT_STORAGE);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NOT_EXTENDED} response.
     * @return a new {@link HttpResponseStatuses#NOT_EXTENDED} response.
     */
    default BlockingStreamingHttpResponse notExtended() {
        return newResponse(NOT_EXTENDED);
    }

    /**
     * Create a new {@link HttpResponseStatuses#NETWORK_AUTHENTICATION_REQUIRED} response.
     * @return a new {@link HttpResponseStatuses#NETWORK_AUTHENTICATION_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse networkAuthenticationRequired() {
        return newResponse(NETWORK_AUTHENTICATION_REQUIRED);
    }
}
