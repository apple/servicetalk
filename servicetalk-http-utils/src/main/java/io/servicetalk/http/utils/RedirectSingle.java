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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TriConsumer;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TrailersTransformer;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.FOUND;
import static io.servicetalk.http.api.HttpResponseStatus.MOVED_PERMANENTLY;
import static java.util.Arrays.binarySearch;

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
    private final Config config;

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
                   final Config config) {
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

        private static final TrailersTransformer<Object, Buffer> NOOP_TRAILERS_TRANSFORMER =
                new StatelessTrailersTransformer<>();
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

            final boolean shouldRedirect;
            try {
                shouldRedirect = shouldRedirect(redirectCount + 1, request, response);
            } catch (final Throwable cause) {
                target.onError(cause);
                return;
            }
            if (!shouldRedirect) {
                target.onSuccess(response);
                return;
            }

            final StreamingHttpRequest newRequest;
            try {
                newRequest = prepareRedirectRequest(request, response, redirectSingle.requester);
            } catch (final Throwable cause) {
                target.onError(cause);
                return;
            }
            if (newRequest == null) {
                target.onSuccess(response);
                return;
            }

            final String newScheme = newRequest.scheme();
            if (isRelative(request, scheme, newRequest)) {
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
                copyAll(request, newRequest);
            } else if (redirectSingle.allowNonRelativeRedirects) {
                copySome(request, newRequest);
            } else {
                LOGGER.debug(
                        "Ignoring non-relative redirect to '{}' for request '{}': Only relative redirects are allowed",
                        newRequest.requestTarget(), request);
                target.onSuccess(response);
                return;
            }

            try {
                redirectSingle.config.prepareRequest.accept(request, response, newRequest);
            } catch (final Throwable cause) {
                target.onError(cause);
                return;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Execute redirect to '{}' for request '{}'", response.headers().get(LOCATION), request);
            }

            // Consume any payload of the redirect response
            toSource(response.messageBody().ignoreElements().concat(
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

        private boolean shouldRedirect(final int redirectCount, final HttpRequestMetaData requestMetaData,
                                       final HttpResponseMetaData responseMetaData) {
            final int statusCode = responseMetaData.status().code();

            if (statusCode < 300 || statusCode > 308) {
                // We start without support for status codes outside of this range
                // re-visit when we need to support payloads in redirects.
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
                    final Config config = redirectSingle.config;
                    if (redirectCount > config.maxRedirects) {
                        LOGGER.debug("Maximum number of redirects ({}) reached for original request: {}",
                                config.maxRedirects, redirectSingle.originalRequest);
                        return false;
                    }

                    if (binarySearch(config.allowedMethods, requestMetaData.method().name()) < 0) {
                        LOGGER.debug("Configuration does not allow redirect of method: {}", requestMetaData.method());
                        return false;
                    }

                    final CharSequence locationHeader = responseMetaData.headers().get(LOCATION);
                    if (locationHeader == null || locationHeader.length() == 0) {
                        LOGGER.debug("No location header in redirect response: {}", responseMetaData);
                        return false;
                    }

                    // Final decision is made by a user's predicate:
                    return config.shouldRedirect.test(requestMetaData, responseMetaData);
            }
        }

        @Nullable
        private StreamingHttpRequest prepareRedirectRequest(final StreamingHttpRequest request,
                                                            final StreamingHttpResponse response,
                                                            final StreamingHttpRequestFactory requestFactory) {
            final HttpRequestMethod method = defineRedirectMethod(response.status().code(), request.method());
            final CharSequence locationHeader = response.headers().get(LOCATION);
            assert locationHeader != null;

            final StreamingHttpRequest redirectRequest =
                    requestFactory.newRequest(method, locationHeader.toString()).version(request.version());

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

        private HttpRequestMethod defineRedirectMethod(final int statusCode, final HttpRequestMethod originalMethod) {
            // https://tools.ietf.org/html/rfc7231#section-6.4.2
            // https://tools.ietf.org/html/rfc7231#section-6.4.3
            // Note for 301 (Moved Permanently) and 302 (Found):
            //     For historical reasons, a user agent MAY change the request method from POST to GET for the
            //     subsequent request.  If this behavior is undesired, the 307 (Temporary Redirect) or
            //     308 (Permanent Redirect) status codes can be used instead.
            return redirectSingle.config.changePostToGet &&
                    (statusCode == MOVED_PERMANENTLY.code() || statusCode == FOUND.code()) &&
                    POST.name().equals(originalMethod.name()) ? GET : originalMethod;
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

        private static void copyAll(final StreamingHttpRequest originalRequest,
                                    final StreamingHttpRequest redirectRequest) {
            redirectRequest.setHeaders(originalRequest.headers());
            redirectRequest.transformMessageBody(p -> p.ignoreElements().concat(originalRequest.messageBody()));
            // Use `transform` to update PayloadInfo flags, assuming trailers may be included in the message body
            redirectRequest.transform(NOOP_TRAILERS_TRANSFORMER);
            // FIXME: instead of `transform`, preserve original PayloadInfo/FlushStrategy when it's API is available
        }

        private void copySome(final StreamingHttpRequest request, final StreamingHttpRequest redirectRequest) {
            // NOTE: for security reasons we do not copy any headers or payload body from the original request by
            // default for non-relative redirects.
            for (CharSequence headerName : redirectSingle.config.headersToRedirect) {
                for (CharSequence headerValue : request.headers().values(headerName)) {
                    redirectRequest.addHeader(headerName, headerValue);
                }
            }

            final Config config = redirectSingle.config;
            if (config.redirectPayloadBody) {
                if (config.trailersToRedirect.length == 0) {
                    redirectRequest.payloadBody(request.payloadBody());
                } else {
                    redirectRequest.transformMessageBody(p -> p.ignoreElements().concat(request.messageBody()))
                            .transform(new StatelessTrailersTransformer<Buffer>() {
                                @Override
                                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                    return filterTrailers(trailers, config.trailersToRedirect);
                                }
                            });
                }
            } else if (config.trailersToRedirect.length != 0) {
                redirectRequest.transformMessageBody(p -> p.ignoreElements().concat(request.messageBody()
                        .filter(item -> item instanceof HttpHeaders)))
                        .transform(new StatelessTrailersTransformer<Buffer>() {
                            @Override
                            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                                return filterTrailers(trailers, config.trailersToRedirect);
                            }
                        });
            }
        }

        private static HttpHeaders filterTrailers(final HttpHeaders trailers, final CharSequence[] keepOnly) {
            Iterator<Map.Entry<CharSequence, CharSequence>> it = trailers.iterator();
            while (it.hasNext()) {
                Map.Entry<CharSequence, CharSequence> entry = it.next();
                if (Arrays.stream(keepOnly).noneMatch(name -> contentEqualsIgnoreCase(entry.getKey(), name))) {
                    it.remove();
                }
            }
            return trailers;
        }
    }

    static final class Config {
        final int maxRedirects;
        final String[] allowedMethods;
        final BiPredicate<HttpRequestMetaData, HttpResponseMetaData> shouldRedirect;
        final boolean changePostToGet;
        final CharSequence[] headersToRedirect;
        final boolean redirectPayloadBody;
        final CharSequence[] trailersToRedirect;
        final TriConsumer<StreamingHttpRequest, StreamingHttpResponse, StreamingHttpRequest> prepareRequest;

        Config(final int maxRedirects, final String[] allowedMethods,
               final BiPredicate<HttpRequestMetaData, HttpResponseMetaData> shouldRedirect,
               final boolean changePostToGet, final CharSequence[] headersToRedirect,
               final boolean redirectPayloadBody, final CharSequence[] trailersToRedirect,
               final TriConsumer<StreamingHttpRequest, StreamingHttpResponse, StreamingHttpRequest> prepareRequest) {
            this.maxRedirects = maxRedirects;
            this.allowedMethods = allowedMethods;
            this.shouldRedirect = shouldRedirect;
            this.changePostToGet = changePostToGet;
            this.headersToRedirect = headersToRedirect;
            this.redirectPayloadBody = redirectPayloadBody;
            this.trailersToRedirect = trailersToRedirect;
            this.prepareRequest = prepareRequest;
        }
    }
}
