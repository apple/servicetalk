/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.RedirectConfig;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.REDIRECTION_3XX;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;

/**
 * An operator, which implements redirect logic for {@link StreamingHttpClient}.
 */
final class RedirectSingle extends SubscribableSingle<StreamingHttpResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectSingle.class);

    private final SingleSource<StreamingHttpResponse> originalResponse;
    private final StreamingHttpRequest originalRequest;
    private final StreamingHttpRequester requester;
    private final boolean allowNonRelativeRedirects;
    private final RedirectConfig config;

    /**
     * Create a new {@link Single}<{@link StreamingHttpResponse}> which will be able to handle redirects.
     *
     * @param requester The {@link StreamingHttpRequester} to send redirected requests.
     * @param originalRequest The original {@link StreamingHttpRequest} which was sent.
     * @param originalResponse The original {@link Single}<{@link StreamingHttpResponse}> for which redirect should be
     * applied.
     * @param allowNonRelativeRedirects Allows following non-relative redirects to different target hosts.
     * @param config Other configuration options.
     */
    RedirectSingle(final StreamingHttpRequester requester,
                   final StreamingHttpRequest originalRequest,
                   final Single<StreamingHttpResponse> originalResponse,
                   final boolean allowNonRelativeRedirects,
                   final RedirectConfig config) {
        this.requester = requester;
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

        // The outer catch drains the response before failing, including for an invalid location.
        @SuppressWarnings("PMD.ExceptionAsFlowControl")
        @Override
        public void onSuccess(@Nullable final StreamingHttpResponse response) {
            if (response == null) {
                target.onSuccess(null);
                return;
            }

            boolean terminalDelivered = false;
            try {
                final String location = redirectLocation(redirectCount, request, response);
                if (location == null) {
                    terminalDelivered = true;
                    target.onSuccess(response);
                    return;
                }

                final UriReference reference;
                final HostAndPort locationHostAndPort;
                try {
                    reference = UriReference.parse(location);
                    locationHostAndPort = reference.hostAndPort();
                } catch (IllegalArgumentException e) {
                    // TODO: fail with an exception type that identifies the server as the cause.
                    throw new IllegalArgumentException(
                            "Server returned an invalid redirect location: " + e.getMessage(), e);
                }
                // https://datatracker.ietf.org/doc/html/rfc9112#section-3.3: an asterisk-form request's target URI has
                // an empty path.
                final String basePath = "*".equals(request.requestTarget()) ? "" : request.rawPath();
                final UriReference base = new UriReference(scheme, authority(request), basePath, request.rawQuery());
                final UriReference redirectUri = base.resolve(reference);
                final boolean relative = isRelative(base, reference, locationHostAndPort, redirectUri);
                final String redirectTarget = redirectTarget(redirectUri, relative, request);
                if (redirectTarget == null ||
                        !redirectSingle.config.redirectPredicate().test(relative, redirectCount, request, response)) {
                    terminalDelivered = true;
                    target.onSuccess(response);
                    return;
                }

                StreamingHttpRequest newRequest = redirectSingle.requester.newRequest(request.method(), redirectTarget)
                        .version(request.version());
                // Carry forward the full context:
                newRequest.context(request.context());

                newRequest = redirectSingle.config.redirectRequestTransformer()
                        .apply(relative, request, response, newRequest);

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Executing redirect to '{}' for request '{}'", location, request);
                }

                // Consume any payload of the redirect response
                final Single<StreamingHttpResponse> nextResponse = response.messageBody().ignoreElements()
                        .concat(redirectSingle.requester.request(newRequest));
                final RedirectSubscriber redirectSubscriber = new RedirectSubscriber(target, redirectSingle, newRequest,
                        redirectCount + 1, sequentialCancellable);
                terminalDelivered = true;   // Mark as "delivered" because we do not own `target` from this point
                toSource(nextResponse).subscribe(redirectSubscriber);
            } catch (Throwable cause) {
                if (!terminalDelivered) {
                    // Drain response payload body before propagating the cause
                    sequentialCancellable.nextCancellable(response.messageBody().ignoreElements()
                            .subscribe(() -> safeOnError(target, cause),
                                    suppressed -> safeOnError(target, addSuppressed(cause, suppressed))));
                } else {
                    LOGGER.info("Ignoring exception from onSuccess of Subscriber {}.", target, cause);
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            target.onError(t);
        }

        /**
         * Returns a value of {@link HttpHeaderNames#LOCATION} header or {@code null} if we should not redirect.
         */
        @Nullable
        private String redirectLocation(final int redirectCount, final HttpRequestMetaData requestMetaData,
                                        final HttpResponseMetaData responseMetaData) {

            final HttpResponseStatus status = responseMetaData.status();
            if (!REDIRECTION_3XX.contains(status)) {
                return null;
            }

            final RedirectConfig config = redirectSingle.config;
            if (redirectCount >= config.maxRedirects()) {
                LOGGER.debug("Maximum number of redirects ({}) reached for original request: {}",
                        config.maxRedirects(), redirectSingle.originalRequest);
                return null;
            }

            if (!config.allowedStatuses().contains(status)) {
                LOGGER.debug("Configuration does not allow redirect for response status: {}", status);
                return null;
            }

            final HttpRequestMethod requestMethod = requestMetaData.method();
            if (!config.allowedMethods().contains(requestMethod)) {
                LOGGER.debug("Configuration does not allow redirect for request method: {}", requestMethod);
                return null;
            }

            final String location = config.locationMapper().apply(requestMetaData, responseMetaData);
            if (location == null) {
                LOGGER.debug("No location identified for redirect response: {}", responseMetaData);
            }
            return location;
        }

        /**
         * Returns the request-target to follow a redirect to {@code redirectUri}, or {@code null} if it can not be
         * followed.
         */
        @Nullable
        private String redirectTarget(final UriReference redirectUri, final boolean relative,
                                      final HttpRequestMetaData request) {
            if (!relative && !redirectSingle.allowNonRelativeRedirects) {
                LOGGER.debug(
                    "Ignoring non-relative redirect to '{}' for request '{}': Only relative redirects are allowed",
                    redirectUri, request);
                return null;
            }
            if (redirectSingle.allowNonRelativeRedirects && redirectUri.scheme != null) {
                final HostAndPort hostAndPort = redirectUri.hostAndPort();
                if (hostAndPort != null && !hostAndPort.hostName().isEmpty()) {
                    return redirectUri.toString();
                }
            }
            if (!relative) {
                LOGGER.debug("Ignoring non-relative redirect to '{}' for request '{}': Unknown scheme or host",
                        redirectUri, request);
                return null;
            }
            if (redirectSingle.allowNonRelativeRedirects && redirectUri.authority == null) {
                // The multi-address client requires a host, but the previous request had none. This is unlikely to
                // happen but still possible for HTTP/1.0.
                return null;
            }
            return redirectUri.originForm();
        }

        // Same precedence as HttpRequestMetaData#effectiveHostAndPort(), but the Host header is left unparsed: a
        // location without an authority doesn't need it.
        @Nullable
        private static String authority(final HttpRequestMetaData request) {
            final String host = request.host();
            if (host != null) {
                final int port = request.port();
                return port < 0 ? host : host + ':' + port;
            }
            final CharSequence hostHeader = request.headers().get(HOST);
            return hostHeader == null || hostHeader.length() == 0 ? null : hostHeader.toString();
        }

        private static boolean isRelative(final UriReference base, final UriReference reference,
                                          @Nullable final HostAndPort to, final UriReference redirectUri) {
            if (reference.scheme == null && reference.authority == null) {
                return true;    // Resolution copies the scheme and authority of the previous request
            }
            final HostAndPort original = base.hostAndPort();
            if (original == null || to == null) {
                return false;
            }
            if (base.scheme != null && redirectUri.scheme != null &&
                    !contentEqualsIgnoreCase(base.scheme, redirectUri.scheme)) {
                return false;
            }
            // ASCII-only comparison: Unicode case folding matches distinct hosts, e.g. U+0131 (dotless i) matches 'i'.
            return contentEqualsIgnoreCase(to.hostName(), original.hostName()) &&
                    inferPort(to.port(), redirectUri.scheme) == inferPort(original.port(), base.scheme);
        }

        // Without a scheme there is no default port, so a missing port only matches another missing port.
        private static int inferPort(final int parsedPort, @Nullable final String scheme) {
            if (parsedPort >= 0 || scheme == null) {
                return parsedPort;
            }
            return contentEqualsIgnoreCase("https", scheme) ? 443 : 80;
        }
    }
}
