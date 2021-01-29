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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static java.util.Objects.requireNonNull;

/**
 * An operator, which implements redirect logic for {@link StreamingHttpClient}.
 */
final class RedirectSingle extends SubscribableSingle<StreamingHttpResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectSingle.class);

    private final HttpExecutionStrategy strategy;
    private final SingleSource<StreamingHttpResponse> originalResponse;
    private final StreamingHttpRequest originalRequest;
    private final int maxRedirects;
    private final StreamingHttpRequester requester;
    private final boolean onlyRelative;

    /**
     * Create a new {@link Single}<{@link StreamingHttpResponse}> which will be able to handle redirects.
     *
     * @param strategy Sets the {@link HttpExecutionStrategy} when performing redirects.
     * @param originalResponse The original {@link Single}<{@link StreamingHttpResponse}> for which redirect should be
     * applied.
     * @param originalRequest The original {@link StreamingHttpRequest} which was sent.
     * @param maxRedirects The maximum number of follow up redirects.
     * @param requester The {@link StreamingHttpRequester} to send redirected requests, must be backed by
     * {@link StreamingHttpClient}.
     * @param onlyRelative Limits the automated redirects to relative paths on the same host and port as the {@code
     * originalRequest}, non-relative redirect responses will be returned as-is.
     */
    RedirectSingle(final HttpExecutionStrategy strategy,
                   final Single<StreamingHttpResponse> originalResponse,
                   final StreamingHttpRequest originalRequest,
                   final int maxRedirects,
                   final StreamingHttpRequester requester,
                   final boolean onlyRelative) {
        this.strategy = strategy;
        this.originalResponse = toSource(originalResponse);
        this.originalRequest = requireNonNull(originalRequest);
        this.maxRedirects = maxRedirects;
        this.requester = requireNonNull(requester);
        this.onlyRelative = onlyRelative;
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super StreamingHttpResponse> subscriber) {
        originalResponse.subscribe(new RedirectSubscriber(subscriber, this, originalRequest));
    }

    private static final class RedirectSubscriber implements Subscriber<StreamingHttpResponse> {

        private final Subscriber<? super StreamingHttpResponse> target;
        private final RedirectSingle redirectSingle;
        private final StreamingHttpRequest request;
        @Nullable
        private final String scheme;
        private final int redirectCount;
        private final SequentialCancellable sequentialCancellable;

        RedirectSubscriber(final Subscriber<? super StreamingHttpResponse> target,
                           final RedirectSingle redirectSingle,
                           final StreamingHttpRequest request) {
            this(target, redirectSingle, request, 0, new SequentialCancellable());
        }

        RedirectSubscriber(final Subscriber<? super StreamingHttpResponse> target,
                           final RedirectSingle redirectSingle,
                           final StreamingHttpRequest request,
                           final int redirectCount,
                           final SequentialCancellable sequentialCancellable) {
            this.target = target;
            this.redirectSingle = redirectSingle;
            this.request = request;
            this.scheme = request.scheme();
            this.redirectCount = redirectCount;
            this.sequentialCancellable = sequentialCancellable;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            sequentialCancellable.nextCancellable(cancellable);
            if (redirectCount == 0) {
                target.onSubscribe(sequentialCancellable);
            }
        }

        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse result) {
            if (result == null || !shouldRedirect(redirectCount + 1, result, request.method())) {

                target.onSuccess(result);
                return;
            }

            final StreamingHttpRequest newRequest;
            try {
                newRequest = prepareRedirectRequest(request, result, redirectSingle.requester);
            } catch (final Throwable cause) {
                target.onError(cause);
                return;
            }
            if (newRequest == null) {
                target.onSuccess(result);
                return;
            }
            String newScheme = newRequest.scheme();

            // Bail on the redirect if non-relative when that was requested or a redirect request is impossible to infer
            if (redirectSingle.onlyRelative) {
                HostAndPort oldEffectiveHostAndPort = request.effectiveHostAndPort();
                HostAndPort newEffectiveHostAndPort = newRequest.effectiveHostAndPort();
                if (oldEffectiveHostAndPort == null || !oldEffectiveHostAndPort.equals(newEffectiveHostAndPort)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Ignoring non-relative redirect to '{}' for original request '{}': {}",
                                newRequest.requestTarget(), redirectSingle.originalRequest,
                                "Only relative redirects are allowed");
                    }
                    target.onSuccess(result);
                    return;
                } else if (newScheme != null) {
                    // Rewrite absolute-form location to relative-form request-target in case only relative redirects
                    // are supported and this is a relative redirect
                    newRequest.requestTarget(
                            absoluteToRelativeFormRequestTarget(newRequest.requestTarget(), newScheme));
                }
            } else if (newScheme == null && scheme != null) {
                // Rewrite relative-form location to absolute-form request-target for multi-address client
                newRequest.requestTarget(scheme + "://" + newRequest.headers().get(HOST) + newRequest.requestTarget());
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Execute redirect to '{}' for original request '{}'",
                        result.headers().get(LOCATION), redirectSingle.originalRequest);
            }
            // Consume any payload of the redirect response
            toSource(result.messageBody().ignoreElements().concat(
                    redirectSingle.requester.request(redirectSingle.strategy, newRequest)))
                        .subscribe(new RedirectSubscriber(
                            target, redirectSingle, newRequest, redirectCount + 1, sequentialCancellable));
        }

        // This code is similar to
        // io.servicetalk.http.netty.DefaultMultiAddressHttpClientBuilder#absoluteToRelativeFormRequestTarget
        // but cannot be shared because we don't have an internal module for http
        private static String absoluteToRelativeFormRequestTarget(final String requestTarget,
                                                                  final String scheme) {
            final int fromIndex = scheme.length() + 3;  // +3 because of "://" delimiter after scheme
            final int relativeReferenceIdx = requestTarget.indexOf('/', fromIndex);
            return relativeReferenceIdx < 0 ? "/" : requestTarget.substring(relativeReferenceIdx);
        }

        @Override
        public void onError(final Throwable t) {
            target.onError(t);
        }

        private boolean shouldRedirect(final int redirectCount, final StreamingHttpResponse response,
                                       final HttpRequestMethod originalMethod) {
            final int statusCode = response.status().code();

            if (statusCode < 300 || statusCode > 308) {
                // We start without support for status codes outside of this range
                // re-visit when we need to support payloads in redirects.
                return false;
            }

            if (redirectCount > redirectSingle.maxRedirects) {
                LOGGER.debug("Maximum number of redirects ({}) reached for original request '{}'",
                        redirectSingle.maxRedirects, redirectSingle.originalRequest);
                return false;
            }

            switch (statusCode) {
                case 304:
                    // https://tools.ietf.org/html/rfc7232#section-4.1
                    // The 304 (Not Modified) status code indicates that the cache value is still fresh and can be used.
                case 305:
                    // https://tools.ietf.org/html/rfc7231#section-6.4.5
                    // The 305 (Use Proxy) status code has been deprecated due to
                    // security concerns regarding in-band configuration of a proxy.
                case 306:
                    // https://tools.ietf.org/html/rfc7231#section-6.4.6
                    // The 306 (Unused) status code is no longer used, and the code is reserved.
                    return false;
                default:
                    // Server should return only 200 status code for TRACE.
                    // We don't see a clear use case for redirect for OPTIONS and CONNECT methods
                    // and will support them later if necessary.
                    if (TRACE.name().equals(originalMethod.name())
                            || OPTIONS.name().equals(originalMethod.name())
                            || CONNECT.name().equals(originalMethod.name())) {
                        return false;
                    }

                    final CharSequence locationHeader = response.headers().get(LOCATION);
                    if (locationHeader == null || locationHeader.length() == 0) {
                        LOGGER.debug("No location header for redirect response");
                        return false;
                    }

                    if (statusCode == 307 || statusCode == 308) {
                        // TODO: remove these cases when we will support repeatable payload
                        return GET.name().equals(originalMethod.name()) || HEAD.name().equals(originalMethod.name());
                    }

                    return true;
            }
        }

        @Nullable
        private static StreamingHttpRequest prepareRedirectRequest(final StreamingHttpRequest request,
                                                                   final StreamingHttpResponse response,
                                                                   final StreamingHttpRequestFactory requestFactory) {
            final HttpRequestMethod method = defineRedirectMethod(request.method());
            final CharSequence locationHeader = response.headers().get(LOCATION);
            assert locationHeader != null;

            final StreamingHttpRequest redirectRequest =
                    requestFactory.newRequest(method, locationHeader.toString()).version(request.version());

            String redirectHost = redirectRequest.host();
            if (redirectHost == null) {
                // origin-form request-target in Location header, extract host & port info from original request
                HostAndPort requestHostAndPort = request.effectiveHostAndPort();
                if (requestHostAndPort == null) {
                    // abort, no HOST header found on the original request, this is typical for HTTP/1.0
                    return null;
                }
                final int redirectPort = requestHostAndPort.port();
                redirectRequest.setHeader(HOST, redirectPort < 0 ? requestHostAndPort.hostName() :
                        requestHostAndPort.hostName() + ':' + redirectPort);
            }

            // TODO CONTENT_LENGTH could be non ZERO, when we will support repeatable payloadBody
            redirectRequest.setHeader(CONTENT_LENGTH, ZERO);

            // NOTE: for security reasons we do not keep any headers from original request.
            // If users need to add some custom or authentication headers, they have to apply them via filters.
            return redirectRequest;
        }

        private static HttpRequestMethod defineRedirectMethod(final HttpRequestMethod originalMethod) {
            // https://tools.ietf.org/html/rfc7231#section-6.4.2
            // https://tools.ietf.org/html/rfc7231#section-6.4.3
            // Note for 301 (Moved Permanently) and 302 (Found):
            //     For historical reasons, a user agent MAY change the request method from POST to GET for the
            //     subsequent request.  If this behavior is undesired, the 307 (Temporary Redirect) or
            //     308 (Permanent Redirect) status codes can be used instead.
            // ServiceTalk follows this historical approach.

            // For 303 (See Other) user agent should perform only a
            // GET or HEAD request: https://tools.ietf.org/html/rfc7231#section-6.4.4
            return HEAD.name().equals(originalMethod.name()) ? HEAD : GET;
            // TODO: It also could be originalMethod for 307 & 308, when we will support repeatable payloadBody
        }
    }
}
