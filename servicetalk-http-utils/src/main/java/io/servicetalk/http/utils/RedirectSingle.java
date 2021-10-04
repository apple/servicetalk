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
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.RedirectConfig;
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
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;

/**
 * An operator, which implements redirect logic for {@link StreamingHttpClient}.
 */
final class RedirectSingle extends SubscribableSingle<StreamingHttpResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectSingle.class);

    private final HttpExecutionStrategy strategy;
    private final SingleSource<StreamingHttpResponse> originalResponse;
    private final StreamingHttpRequest originalRequest;
    private final StreamingHttpRequester requester;
    private final boolean allowNonRelativeRedirects;
    private final RedirectConfig config;

    /**
     * Create a new {@link Single}<{@link StreamingHttpResponse}> which will be able to handle redirects.
     *
     * @param requester The {@link StreamingHttpRequester} to send redirected requests.
     * @param strategy Sets the {@link HttpExecutionStrategy} when performing redirects.
     * @param originalRequest The original {@link StreamingHttpRequest} which was sent.
     * @param originalResponse The original {@link Single}<{@link StreamingHttpResponse}> for which redirect should be
     * applied.
     * @param allowNonRelativeRedirects Allows following non-relative redirects to different target hosts.
     * @param config Other configuration options.
     */
    RedirectSingle(final StreamingHttpRequester requester,
                   final HttpExecutionStrategy strategy,
                   final StreamingHttpRequest originalRequest,
                   final Single<StreamingHttpResponse> originalResponse,
                   final boolean allowNonRelativeRedirects,
                   final RedirectConfig config) {
        this.requester = requester;
        this.strategy = strategy;
        this.originalRequest = originalRequest;
        this.originalResponse = toSource(originalResponse);
        this.allowNonRelativeRedirects = allowNonRelativeRedirects;
        this.config = config;
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
            // Remember scheme here because it can be wiped by multi-address client later as part of processing:
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
        public void onSuccess(@Nullable final StreamingHttpResponse response) {
            if (response == null) {
                target.onSuccess(null);
                return;
            }

            boolean terminalDelivered = false;
            try {
                final String location = redirectLocation(redirectCount, request.method(), response);
                if (location == null) {
                    terminalDelivered = true;
                    target.onSuccess(response);
                    return;
                }

                StreamingHttpRequest newRequest = prepareRedirectRequest(request, redirectSingle.requester, location);
                if (newRequest == null) {
                    terminalDelivered = true;
                    target.onSuccess(response);
                    return;
                }

                final String newScheme = newRequest.scheme();
                final boolean relative = isRelative(request, scheme, newRequest);
                if (!relative && !redirectSingle.allowNonRelativeRedirects) {
                    LOGGER.debug(
                        "Ignoring non-relative redirect to '{}' for request '{}': Only relative redirects are allowed",
                        newRequest.requestTarget(), request);
                    terminalDelivered = true;
                    target.onSuccess(response);
                    return;
                }

                if (!redirectSingle.config.redirectPredicate().test(relative, redirectCount, request, response)) {
                    terminalDelivered = true;
                    target.onSuccess(response);
                    return;
                }

                if (relative) {
                    if (redirectSingle.allowNonRelativeRedirects && newScheme == null && scheme != null) {
                        // Rewrite origin-form location to absolute-form request-target for multi-address client:
                        newRequest.requestTarget(scheme + "://" + newRequest.headers().get(HOST) +
                                newRequest.requestTarget());
                    }
                    if (!redirectSingle.allowNonRelativeRedirects && newScheme != null) {
                        // Rewrite absolute-form location to origin-form request-target in case only relative redirects
                        // are supported:
                        newRequest.requestTarget(
                                absoluteToRelativeFormRequestTarget(newRequest.requestTarget(), newScheme));
                    }
                }

                newRequest = redirectSingle.config.redirectRequestTransformer()
                        .apply(relative, request, response, newRequest);

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Executing redirect to '{}' for request '{}'", location, request);
                }

                terminalDelivered = true;   // Mark as "delivered" because we do not own `target` from this point
                toSource(response.messageBody().ignoreElements()    // Consume any payload of the redirect response
                        .concat(redirectSingle.requester.request(redirectSingle.strategy, newRequest)))
                        .subscribe(new RedirectSubscriber(target, redirectSingle, newRequest, redirectCount + 1,
                                sequentialCancellable));
            } catch (Throwable cause) {
                if (!terminalDelivered) {
                    safeOnError(target, cause);
                } else {
                    LOGGER.info("Ignoring exception from onSuccess of Subscriber {}.", target, cause);
                }
            }
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

        /**
         * Returns a value of {@link HttpHeaderNames#LOCATION} header or {@code null} if we should not redirect.
         */
        @Nullable
        private String redirectLocation(final int redirectCount, final HttpRequestMethod requestMethod,
                                        final HttpResponseMetaData responseMetaData) {
            final int statusCode = responseMetaData.status().code();

            if (statusCode < 300 || statusCode > 308 || (statusCode >= 304 && statusCode <= 306)) {
                // We start without support for status codes outside of the standard range

                // https://tools.ietf.org/html/rfc7232#section-4.1
                // The 304 (Not Modified) status code indicates that the cache value is still fresh and can be used.

                // https://tools.ietf.org/html/rfc7231#section-6.4.5
                // The 305 (Use Proxy) status code has been deprecated due to security concerns regarding in-band
                // configuration of a proxy.

                // https://tools.ietf.org/html/rfc7231#section-6.4.6
                // The 306 (Unused) status code is no longer used, and the code is reserved.
                return null;
            }

            final RedirectConfig config = redirectSingle.config;
            if (redirectCount >= config.maxRedirects()) {
                LOGGER.debug("Maximum number of redirects ({}) reached for original request: {}",
                        config.maxRedirects(), redirectSingle.originalRequest);
                return null;
            }

            if (!config.allowedMethods().contains(requestMethod)) {
                LOGGER.debug("Configuration does not allow redirect of method: {}", requestMethod);
                return null;
            }

            final CharSequence locationHeader = responseMetaData.headers().get(LOCATION);
            if (locationHeader == null || locationHeader.length() == 0) {
                LOGGER.debug("No location header in redirect response: {}", responseMetaData);
                return null;
            }

            return locationHeader.toString();
        }

        @Nullable
        private StreamingHttpRequest prepareRedirectRequest(final StreamingHttpRequest request,
                                                            final StreamingHttpRequestFactory requestFactory,
                                                            final String redirectLocation) {
            final StreamingHttpRequest redirectRequest =
                    requestFactory.newRequest(request.method(), redirectLocation).version(request.version());

            String redirectHost = redirectRequest.host();
            if (redirectHost == null && redirectSingle.allowNonRelativeRedirects) {
                // origin-form request-target in Location header, extract host & port info from the previous request:
                HostAndPort requestHostAndPort = request.effectiveHostAndPort();
                if (requestHostAndPort == null) {
                    // abort, no HOST header found on the previous request but required for the multi-address client.
                    // this is unlikely to happen but still possible for HTTP/1.0
                    return null;
                }
                final int redirectPort = requestHostAndPort.port();
                redirectRequest.setHeader(HOST, redirectPort < 0 ? requestHostAndPort.hostName() :
                        requestHostAndPort.hostName() + ':' + redirectPort);
            }
            // nothing to do if non-relative redirects are not allowed
            return redirectRequest;
        }

        private static boolean isRelative(final HttpRequestMetaData originalRequest,
                                          @Nullable final String originalScheme,
                                          final HttpRequestMetaData redirectRequest) {
            final String toHost = redirectRequest.host();
            if (toHost == null) {
                return true;
            }
            final HostAndPort original = originalRequest.effectiveHostAndPort();
            if (original == null) {
                return false;   // Can not extract host and port from the original request => no guarantee it's relative
            }
            if (!toHost.equalsIgnoreCase(original.hostName())) {
                return false;
            }
            return inferPort(redirectRequest.port(), redirectRequest.scheme(), original.port()) ==
                    inferPort(original.port(), originalScheme, original.port());
        }

        private static int inferPort(final int parsedPort, @Nullable final String scheme,
                                     final int fallbackPort) {
            if (parsedPort >= 0) {
                return parsedPort;
            }
            if (scheme == null) {
                return fallbackPort;
            }
            return "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }
    }
}
