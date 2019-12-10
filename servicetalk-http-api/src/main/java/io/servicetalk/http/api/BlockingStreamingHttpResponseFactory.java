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

import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.ALREADY_REPORTED;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.CONFLICT;
import static io.servicetalk.http.api.HttpResponseStatus.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatus.CREATED;
import static io.servicetalk.http.api.HttpResponseStatus.EARLY_HINTS;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.FAILED_DEPENDENCY;
import static io.servicetalk.http.api.HttpResponseStatus.FORBIDDEN;
import static io.servicetalk.http.api.HttpResponseStatus.FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.GONE;
import static io.servicetalk.http.api.HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED;
import static io.servicetalk.http.api.HttpResponseStatus.IM_USED;
import static io.servicetalk.http.api.HttpResponseStatus.INSUFFICIENT_STORAGE;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.LENGTH_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.LOCKED;
import static io.servicetalk.http.api.HttpResponseStatus.LOOP_DETECTED;
import static io.servicetalk.http.api.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.servicetalk.http.api.HttpResponseStatus.MISDIRECTED_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.servicetalk.http.api.HttpResponseStatus.MULTIPLE_CHOICES;
import static io.servicetalk.http.api.HttpResponseStatus.MULTI_STATUS;
import static io.servicetalk.http.api.HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_ACCEPTABLE;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_EXTENDED;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PARTIAL_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.PAYMENT_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.PERMANENT_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatus.PRECONDITION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.PRECONDITION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.PROCESSING;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
import static io.servicetalk.http.api.HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
import static io.servicetalk.http.api.HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE;
import static io.servicetalk.http.api.HttpResponseStatus.REQUEST_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.REQUEST_URI_TOO_LONG;
import static io.servicetalk.http.api.HttpResponseStatus.RESET_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.SEE_OTHER;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.api.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.servicetalk.http.api.HttpResponseStatus.TEMPORARY_REDIRECT;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.http.api.HttpResponseStatus.UNAUTHORIZED;
import static io.servicetalk.http.api.HttpResponseStatus.UNAVAILABLE_FOR_LEGAL_REASONS;
import static io.servicetalk.http.api.HttpResponseStatus.UNORDERED_COLLECTION;
import static io.servicetalk.http.api.HttpResponseStatus.UNPROCESSABLE_ENTITY;
import static io.servicetalk.http.api.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
import static io.servicetalk.http.api.HttpResponseStatus.UPGRADE_REQUIRED;
import static io.servicetalk.http.api.HttpResponseStatus.USE_PROXY;
import static io.servicetalk.http.api.HttpResponseStatus.VARIANT_ALSO_NEGOTIATES;

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
     * Create a new {@link HttpResponseStatus#CONTINUE} response.
     * @return a new {@link HttpResponseStatus#CONTINUE} response.
     */
    default BlockingStreamingHttpResponse continueResponse() {
        return newResponse(CONTINUE);
    }

    /**
     * Create a new {@link HttpResponseStatus#SWITCHING_PROTOCOLS} response.
     * @return a new {@link HttpResponseStatus#SWITCHING_PROTOCOLS} response.
     */
    default BlockingStreamingHttpResponse switchingProtocols() {
        return newResponse(SWITCHING_PROTOCOLS);
    }

    /**
     * Create a new {@link HttpResponseStatus#PROCESSING} response.
     * @return a new {@link HttpResponseStatus#PROCESSING} response.
     */
    default BlockingStreamingHttpResponse processing() {
        return newResponse(PROCESSING);
    }

    /**
     * Create a new {@link HttpResponseStatus#EARLY_HINTS} response.
     * @return a new {@link HttpResponseStatus#EARLY_HINTS} response.
     */
    default BlockingStreamingHttpResponse earlyHints() {
        return newResponse(EARLY_HINTS);
    }

    /**
     * Create a new {@link HttpResponseStatus#OK} response.
     * @return a new {@link HttpResponseStatus#OK} response.
     */
    default BlockingStreamingHttpResponse ok() {
        return newResponse(OK);
    }

    /**
     * Create a new {@link HttpResponseStatus#CREATED} response.
     * @return a new {@link HttpResponseStatus#CREATED} response.
     */
    default BlockingStreamingHttpResponse created() {
        return newResponse(CREATED);
    }

    /**
     * Create a new {@link HttpResponseStatus#ACCEPTED} response.
     * @return a new {@link HttpResponseStatus#ACCEPTED} response.
     */
    default BlockingStreamingHttpResponse accepted() {
        return newResponse(ACCEPTED);
    }

    /**
     * Create a new {@link HttpResponseStatus#NON_AUTHORITATIVE_INFORMATION} response.
     * @return a new {@link HttpResponseStatus#NON_AUTHORITATIVE_INFORMATION} response.
     */
    default BlockingStreamingHttpResponse nonAuthoritativeInformation() {
        return newResponse(NON_AUTHORITATIVE_INFORMATION);
    }

    /**
     * Create a new {@link HttpResponseStatus#NO_CONTENT} response.
     * @return a new {@link HttpResponseStatus#NO_CONTENT} response.
     */
    default BlockingStreamingHttpResponse noContent() {
        return newResponse(NO_CONTENT);
    }

    /**
     * Create a new {@link HttpResponseStatus#RESET_CONTENT} response.
     * @return a new {@link HttpResponseStatus#RESET_CONTENT} response.
     */
    default BlockingStreamingHttpResponse resetContent() {
        return newResponse(RESET_CONTENT);
    }

    /**
     * Create a new {@link HttpResponseStatus#PARTIAL_CONTENT} response.
     * @return a new {@link HttpResponseStatus#PARTIAL_CONTENT} response.
     */
    default BlockingStreamingHttpResponse partialContent() {
        return newResponse(PARTIAL_CONTENT);
    }

    /**
     * Create a new {@link HttpResponseStatus#MULTI_STATUS} response.
     * @return a new {@link HttpResponseStatus#MULTI_STATUS} response.
     */
    default BlockingStreamingHttpResponse multiStatus() {
        return newResponse(MULTI_STATUS);
    }

    /**
     * Create a new {@link HttpResponseStatus#ALREADY_REPORTED} response.
     * @return a new {@link HttpResponseStatus#ALREADY_REPORTED} response.
     */
    default BlockingStreamingHttpResponse alreadyReported() {
        return newResponse(ALREADY_REPORTED);
    }

    /**
     * Create a new {@link HttpResponseStatus#IM_USED} response.
     * @return a new {@link HttpResponseStatus#IM_USED} response.
     */
    default BlockingStreamingHttpResponse imUsed() {
        return newResponse(IM_USED);
    }

    /**
     * Create a new {@link HttpResponseStatus#MULTIPLE_CHOICES} response.
     * @return a new {@link HttpResponseStatus#MULTIPLE_CHOICES} response.
     */
    default BlockingStreamingHttpResponse multipleChoices() {
        return newResponse(MULTIPLE_CHOICES);
    }

    /**
     * Create a new {@link HttpResponseStatus#MOVED_PERMANENTLY} response.
     * @return a new {@link HttpResponseStatus#MOVED_PERMANENTLY} response.
     */
    default BlockingStreamingHttpResponse movedPermanently() {
        return newResponse(MOVED_PERMANENTLY);
    }

    /**
     * Create a new {@link HttpResponseStatus#FOUND} response.
     * @return a new {@link HttpResponseStatus#FOUND} response.
     */
    default BlockingStreamingHttpResponse found() {
        return newResponse(FOUND);
    }

    /**
     * Create a new {@link HttpResponseStatus#SEE_OTHER} response.
     * @return a new {@link HttpResponseStatus#SEE_OTHER} response.
     */
    default BlockingStreamingHttpResponse seeOther() {
        return newResponse(SEE_OTHER);
    }

    /**
     * Create a new {@link HttpResponseStatus#NOT_MODIFIED} response.
     * @return a new {@link HttpResponseStatus#NOT_MODIFIED} response.
     */
    default BlockingStreamingHttpResponse notModified() {
        return newResponse(NOT_MODIFIED);
    }

    /**
     * Create a new {@link HttpResponseStatus#USE_PROXY} response.
     * @return a new {@link HttpResponseStatus#USE_PROXY} response.
     */
    default BlockingStreamingHttpResponse useProxy() {
        return newResponse(USE_PROXY);
    }

    /**
     * Create a new {@link HttpResponseStatus#TEMPORARY_REDIRECT} response.
     * @return a new {@link HttpResponseStatus#TEMPORARY_REDIRECT} response.
     */
    default BlockingStreamingHttpResponse temporaryRedirect() {
        return newResponse(TEMPORARY_REDIRECT);
    }

    /**
     * Create a new {@link HttpResponseStatus#PERMANENT_REDIRECT} response.
     * @return a new {@link HttpResponseStatus#PERMANENT_REDIRECT} response.
     */
    default BlockingStreamingHttpResponse permanentRedirect() {
        return newResponse(PERMANENT_REDIRECT);
    }

    /**
     * Create a new {@link HttpResponseStatus#BAD_REQUEST} response.
     * @return a new {@link HttpResponseStatus#BAD_REQUEST} response.
     */
    default BlockingStreamingHttpResponse badRequest() {
        return newResponse(BAD_REQUEST);
    }

    /**
     * Create a new {@link HttpResponseStatus#UNAUTHORIZED} response.
     * @return a new {@link HttpResponseStatus#UNAUTHORIZED} response.
     */
    default BlockingStreamingHttpResponse unauthorized() {
        return newResponse(UNAUTHORIZED);
    }

    /**
     * Create a new {@link HttpResponseStatus#PAYMENT_REQUIRED} response.
     * @return a new {@link HttpResponseStatus#PAYMENT_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse paymentRequired() {
        return newResponse(PAYMENT_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatus#FORBIDDEN} response.
     * @return a new {@link HttpResponseStatus#FORBIDDEN} response.
     */
    default BlockingStreamingHttpResponse forbidden() {
        return newResponse(FORBIDDEN);
    }

    /**
     * Create a new {@link HttpResponseStatus#NOT_FOUND} response.
     * @return a new {@link HttpResponseStatus#NOT_FOUND} response.
     */
    default BlockingStreamingHttpResponse notFound() {
        return newResponse(NOT_FOUND);
    }

    /**
     * Create a new {@link HttpResponseStatus#METHOD_NOT_ALLOWED} response.
     * @return a new {@link HttpResponseStatus#METHOD_NOT_ALLOWED} response.
     */
    default BlockingStreamingHttpResponse methodNotAllowed() {
        return newResponse(METHOD_NOT_ALLOWED);
    }

    /**
     * Create a new {@link HttpResponseStatus#NOT_ACCEPTABLE} response.
     * @return a new {@link HttpResponseStatus#NOT_ACCEPTABLE} response.
     */
    default BlockingStreamingHttpResponse notAcceptable() {
        return newResponse(NOT_ACCEPTABLE);
    }

    /**
     * Create a new {@link HttpResponseStatus#PROXY_AUTHENTICATION_REQUIRED} response.
     * @return a new {@link HttpResponseStatus#PROXY_AUTHENTICATION_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse proxyAuthenticationRequired() {
        return newResponse(PROXY_AUTHENTICATION_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatus#REQUEST_TIMEOUT} response.
     * @return a new {@link HttpResponseStatus#REQUEST_TIMEOUT} response.
     */
    default BlockingStreamingHttpResponse requestTimeout() {
        return newResponse(REQUEST_TIMEOUT);
    }

    /**
     * Create a new {@link HttpResponseStatus#CONFLICT} response.
     * @return a new {@link HttpResponseStatus#CONFLICT} response.
     */
    default BlockingStreamingHttpResponse conflict() {
        return newResponse(CONFLICT);
    }

    /**
     * Create a new {@link HttpResponseStatus#GONE} response.
     * @return a new {@link HttpResponseStatus#GONE} response.
     */
    default BlockingStreamingHttpResponse gone() {
        return newResponse(GONE);
    }

    /**
     * Create a new {@link HttpResponseStatus#LENGTH_REQUIRED} response.
     * @return a new {@link HttpResponseStatus#LENGTH_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse lengthRequired() {
        return newResponse(LENGTH_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatus#PRECONDITION_FAILED} response.
     * @return a new {@link HttpResponseStatus#PRECONDITION_FAILED} response.
     */
    default BlockingStreamingHttpResponse preconditionFailed() {
        return newResponse(PRECONDITION_FAILED);
    }

    /**
     * Create a new {@link HttpResponseStatus#REQUEST_ENTITY_TOO_LARGE} response.
     * @return a new {@link HttpResponseStatus#REQUEST_ENTITY_TOO_LARGE} response.
     */
    default BlockingStreamingHttpResponse requestEntityTooLarge() {
        return newResponse(REQUEST_ENTITY_TOO_LARGE);
    }

    /**
     * Create a new {@link HttpResponseStatus#REQUEST_URI_TOO_LONG} response.
     * @return a new {@link HttpResponseStatus#REQUEST_URI_TOO_LONG} response.
     */
    default BlockingStreamingHttpResponse requestUriTooLong() {
        return newResponse(REQUEST_URI_TOO_LONG);
    }

    /**
     * Create a new {@link HttpResponseStatus#UNSUPPORTED_MEDIA_TYPE} response.
     * @return a new {@link HttpResponseStatus#UNSUPPORTED_MEDIA_TYPE} response.
     */
    default BlockingStreamingHttpResponse unsupportedMediaType() {
        return newResponse(UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Create a new {@link HttpResponseStatus#REQUESTED_RANGE_NOT_SATISFIABLE} response.
     * @return a new {@link HttpResponseStatus#REQUESTED_RANGE_NOT_SATISFIABLE} response.
     */
    default BlockingStreamingHttpResponse requestedRangeNotSatisfiable() {
        return newResponse(REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    /**
     * Create a new {@link HttpResponseStatus#EXPECTATION_FAILED} response.
     * @return a new {@link HttpResponseStatus#EXPECTATION_FAILED} response.
     */
    default BlockingStreamingHttpResponse expectationFailed() {
        return newResponse(EXPECTATION_FAILED);
    }

    /**
     * Create a new {@link HttpResponseStatus#MISDIRECTED_REQUEST} response.
     * @return a new {@link HttpResponseStatus#MISDIRECTED_REQUEST} response.
     */
    default BlockingStreamingHttpResponse misdirectedRequest() {
        return newResponse(MISDIRECTED_REQUEST);
    }

    /**
     * Create a new {@link HttpResponseStatus#UNPROCESSABLE_ENTITY} response.
     * @return a new {@link HttpResponseStatus#UNPROCESSABLE_ENTITY} response.
     */
    default BlockingStreamingHttpResponse unprocessableEntity() {
        return newResponse(UNPROCESSABLE_ENTITY);
    }

    /**
     * Create a new {@link HttpResponseStatus#LOCKED} response.
     * @return a new {@link HttpResponseStatus#LOCKED} response.
     */
    default BlockingStreamingHttpResponse locked() {
        return newResponse(LOCKED);
    }

    /**
     * Create a new {@link HttpResponseStatus#FAILED_DEPENDENCY} response.
     * @return a new {@link HttpResponseStatus#FAILED_DEPENDENCY} response.
     */
    default BlockingStreamingHttpResponse failedDependency() {
        return newResponse(FAILED_DEPENDENCY);
    }

    /**
     * Create a new {@link HttpResponseStatus#UNORDERED_COLLECTION} response.
     * @return a new {@link HttpResponseStatus#UNORDERED_COLLECTION} response.
     */
    default BlockingStreamingHttpResponse unorderedCollection() {
        return newResponse(UNORDERED_COLLECTION);
    }

    /**
     * Create a new {@link HttpResponseStatus#UPGRADE_REQUIRED} response.
     * @return a new {@link HttpResponseStatus#UPGRADE_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse upgradeRequired() {
        return newResponse(UPGRADE_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatus#PRECONDITION_REQUIRED} response.
     * @return a new {@link HttpResponseStatus#PRECONDITION_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse preconditionRequired() {
        return newResponse(PRECONDITION_REQUIRED);
    }

    /**
     * Create a new {@link HttpResponseStatus#TOO_MANY_REQUESTS} response.
     * @return a new {@link HttpResponseStatus#TOO_MANY_REQUESTS} response.
     */
    default BlockingStreamingHttpResponse tooManyRequests() {
        return newResponse(TOO_MANY_REQUESTS);
    }

    /**
     * Create a new {@link HttpResponseStatus#REQUEST_HEADER_FIELDS_TOO_LARGE} response.
     * @return a new {@link HttpResponseStatus#REQUEST_HEADER_FIELDS_TOO_LARGE} response.
     */
    default BlockingStreamingHttpResponse requestHeaderFieldsTooLarge() {
        return newResponse(REQUEST_HEADER_FIELDS_TOO_LARGE);
    }

    /**
     * Create a new {@link HttpResponseStatus#UNAVAILABLE_FOR_LEGAL_REASONS} response.
     * @return a new {@link HttpResponseStatus#UNAVAILABLE_FOR_LEGAL_REASONS} response.
     */
    default BlockingStreamingHttpResponse unavailableForLegalReasons() {
        return newResponse(UNAVAILABLE_FOR_LEGAL_REASONS);
    }

    /**
     * Create a new {@link HttpResponseStatus#INTERNAL_SERVER_ERROR} response.
     * @return a new {@link HttpResponseStatus#INTERNAL_SERVER_ERROR} response.
     */
    default BlockingStreamingHttpResponse internalServerError() {
        return newResponse(INTERNAL_SERVER_ERROR);
    }

    /**
     * Create a new {@link HttpResponseStatus#NOT_IMPLEMENTED} response.
     * @return a new {@link HttpResponseStatus#NOT_IMPLEMENTED} response.
     */
    default BlockingStreamingHttpResponse notImplemented() {
        return newResponse(NOT_IMPLEMENTED);
    }

    /**
     * Create a new {@link HttpResponseStatus#BAD_GATEWAY} response.
     * @return a new {@link HttpResponseStatus#BAD_GATEWAY} response.
     */
    default BlockingStreamingHttpResponse badGateway() {
        return newResponse(BAD_GATEWAY);
    }

    /**
     * Create a new {@link HttpResponseStatus#SERVICE_UNAVAILABLE} response.
     * @return a new {@link HttpResponseStatus#SERVICE_UNAVAILABLE} response.
     */
    default BlockingStreamingHttpResponse serviceUnavailable() {
        return newResponse(SERVICE_UNAVAILABLE);
    }

    /**
     * Create a new {@link HttpResponseStatus#GATEWAY_TIMEOUT} response.
     * @return a new {@link HttpResponseStatus#GATEWAY_TIMEOUT} response.
     */
    default BlockingStreamingHttpResponse gatewayTimeout() {
        return newResponse(GATEWAY_TIMEOUT);
    }

    /**
     * Create a new {@link HttpResponseStatus#HTTP_VERSION_NOT_SUPPORTED} response.
     * @return a new {@link HttpResponseStatus#HTTP_VERSION_NOT_SUPPORTED} response.
     */
    default BlockingStreamingHttpResponse httpVersionNotSupported() {
        return newResponse(HTTP_VERSION_NOT_SUPPORTED);
    }

    /**
     * Create a new {@link HttpResponseStatus#VARIANT_ALSO_NEGOTIATES} response.
     * @return a new {@link HttpResponseStatus#VARIANT_ALSO_NEGOTIATES} response.
     */
    default BlockingStreamingHttpResponse variantAlsoNegotiates() {
        return newResponse(VARIANT_ALSO_NEGOTIATES);
    }

    /**
     * Create a new {@link HttpResponseStatus#INSUFFICIENT_STORAGE} response.
     * @return a new {@link HttpResponseStatus#INSUFFICIENT_STORAGE} response.
     */
    default BlockingStreamingHttpResponse insufficientStorage() {
        return newResponse(INSUFFICIENT_STORAGE);
    }

    /**
     * Create a new {@link HttpResponseStatus#LOOP_DETECTED} response.
     * @return a new {@link HttpResponseStatus#LOOP_DETECTED} response.
     */
    default BlockingStreamingHttpResponse loopDetected() {
        return newResponse(LOOP_DETECTED);
    }

    /**
     * Create a new {@link HttpResponseStatus#NOT_EXTENDED} response.
     * @return a new {@link HttpResponseStatus#NOT_EXTENDED} response.
     */
    default BlockingStreamingHttpResponse notExtended() {
        return newResponse(NOT_EXTENDED);
    }

    /**
     * Create a new {@link HttpResponseStatus#NETWORK_AUTHENTICATION_REQUIRED} response.
     * @return a new {@link HttpResponseStatus#NETWORK_AUTHENTICATION_REQUIRED} response.
     */
    default BlockingStreamingHttpResponse networkAuthenticationRequired() {
        return newResponse(NETWORK_AUTHENTICATION_REQUIRED);
    }
}
