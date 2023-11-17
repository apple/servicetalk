/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpLifecycleObserver;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.slf4j.internal.FixedLevelLogger;
import io.servicetalk.transport.api.ConnectionInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingHttpLifecycleObserverTest {
    private HttpRequestMetaData mockRequestMetadata;
    private HttpResponseMetaData mockResponseMetadata;
    private FixedLevelLogger mockLogger;

    private HttpLifecycleObserver.HttpExchangeObserver observer;

    @BeforeEach
    void setUp() {
        mockRequestMetadata = mock(HttpRequestMetaData.class);
        when(mockRequestMetadata.headers()).thenReturn(mock(HttpHeaders.class));
        when(mockRequestMetadata.method()).thenReturn(HttpRequestMethod.GET);
        when(mockRequestMetadata.requestTarget()).thenReturn("/foo/bar");
        when(mockRequestMetadata.version()).thenReturn(HttpProtocolVersion.HTTP_2_0);

        mockResponseMetadata = mock(HttpResponseMetaData.class);
        when(mockResponseMetadata.status()).thenReturn(HttpResponseStatus.OK);
        when(mockResponseMetadata.headers()).thenReturn(mock(HttpHeaders.class));

        mockLogger = mock(FixedLevelLogger.class);
        when(mockLogger.logLevel()).thenReturn(LogLevel.INFO);

        observer = new LoggingHttpLifecycleObserver(mockLogger).onNewExchange();
    }

    @Test
    void testFactoryMethod() {
        assertThat(observer, isA(HttpLifecycleObserver.HttpRequestObserver.class));
        assertThat(observer, isA(HttpLifecycleObserver.HttpResponseObserver.class));
    }

    @Test
    void testOnRequestError() {
        observer.onConnectionSelected(mock(ConnectionInfo.class));
        Throwable requestError = new DeliberateException();
        observer.onRequest(mockRequestMetadata);
        ((HttpLifecycleObserver.HttpRequestObserver) observer).onRequestError(requestError);
        observer.onResponseCancel();
        observer.onExchangeFinally();
        verify(mockLogger).log(anyString(),
                any(), // conn info
                eq(HttpRequestMethod.GET), eq("/foo/bar"), eq(HttpProtocolVersion.HTTP_2_0), eq(0),
                eq(0L), // request size
                eq(0), // requestTrailersCount
                any(), // unwrappedRequestResult
                any(), // unwrappedResponseResult
                anyLong(), // responseTimeMs
                anyLong(), // duration
                eq(requestError));
    }

    @Test
    void testOnResponseError() {
        observer.onConnectionSelected(mock(ConnectionInfo.class));
        observer.onRequest(mockRequestMetadata);
        Throwable responseError = new DeliberateException();
        observer.onResponseError(responseError);
        observer.onExchangeFinally();
        verify(mockLogger).log(anyString(),
                any(), // conn info
                eq(HttpRequestMethod.GET), eq("/foo/bar"), eq(HttpProtocolVersion.HTTP_2_0), eq(0),
                eq(0L), // request size
                eq(0), // requestTrailersCount
                any(), // unwrappedRequestResult
                any(), // unwrappedResponseResult
                anyLong(), // responseTimeMs
                anyLong(), // duration
                eq(responseError));
    }

    @Test
    void testCombinedError() {
        observer.onConnectionSelected(mock(ConnectionInfo.class));
        Throwable requestError = new DeliberateException();
        Throwable responseError = new DeliberateException();
        observer.onRequest(mockRequestMetadata);
        ((HttpLifecycleObserver.HttpRequestObserver) observer).onRequestError(requestError);
        observer.onResponseError(responseError);
        observer.onExchangeFinally();
        verify(mockLogger).log(anyString(),
                any(), // conn info
                eq(HttpRequestMethod.GET), eq("/foo/bar"), eq(HttpProtocolVersion.HTTP_2_0), eq(0),
                eq(0L), // request size
                any(), // requestTrailersCount
                any(), // unwrappedRequestResult
                any(), // unwrappedResponseResult
                anyLong(), // responseTimeMs
                anyLong(), // duration
                eq(responseError));
        assertThat(responseError.getSuppressed()[0], is(requestError));
    }

    @Test
    void testOnResponseErrorWithMetadata() {
        observer.onConnectionSelected(mock(ConnectionInfo.class));
        observer.onRequest(mockRequestMetadata);
        Throwable responseError = new DeliberateException();
        observer.onResponse(mockResponseMetadata);
        observer.onResponseError(responseError);
        observer.onExchangeFinally();
        verify(mockLogger).log(anyString(),
                any(), // conn info
                eq(HttpRequestMethod.GET), eq("/foo/bar"), eq(HttpProtocolVersion.HTTP_2_0), eq(0),
                eq(0L), // request size
                eq(0), // requestTrailersCount
                any(), // unwrappedRequestResult
                eq(HttpResponseStatus.OK.code()), // responseCode
                eq(0), // response Header Size
                eq(0L), // responseSize
                eq(0), // responseTrailersCount
                any(), // unwrappedResponseResult
                anyLong(), // responseTimeMs
                anyLong(), // duration
                eq(responseError));
    }
}
