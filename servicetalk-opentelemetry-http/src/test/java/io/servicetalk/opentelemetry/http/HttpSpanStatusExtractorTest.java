/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpRequest;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class HttpSpanStatusExtractorTest {

    @Mock
    private SpanStatusBuilder spanStatusBuilder;

    @Mock
    StreamingHttpRequest requestMetaData;

    @Mock
    HttpResponseMetaData responseMetaData;

    @ParameterizedTest(name = "{displayName} [{index}]: isServer={0}")
    @ValueSource(booleans = {true, false})
    void testStatus200To399(boolean isServer) {
        RequestInfo requestInfo = new RequestInfo(requestMetaData, null);
        for (int code = 100; code < 400; code++) {
            when(responseMetaData.status()).thenReturn(HttpResponseStatus.of(code, "any"));
            getExtractor(isServer).extract(spanStatusBuilder, requestInfo, responseMetaData, null);
        }
        // Should remain at the default value of UNSET
        verify(spanStatusBuilder, times(0)).setStatus(any());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: isServer={0}")
    @ValueSource(booleans = {true, false})
    void testStatus400to499(boolean isServer) {
        RequestInfo requestInfo = new RequestInfo(requestMetaData, null);
        int executions = 0;
        for (int code = 400; code < 500; code++) {
            executions++;
            when(responseMetaData.status()).thenReturn(HttpResponseStatus.of(code, "any"));
            getExtractor(isServer).extract(spanStatusBuilder, requestInfo, responseMetaData, null);
        }
        if (isServer) {
            // Should remain at the default value of UNSET
            verify(spanStatusBuilder, times(0)).setStatus(any());
        } else {
            verify(spanStatusBuilder, times(executions)).setStatus(StatusCode.ERROR);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: isServer={0}")
    @ValueSource(booleans = {true, false})
    void testStatus500to599(boolean isServer) {
        RequestInfo requestInfo = new RequestInfo(requestMetaData, null);
        int executions = 0;
        for (int code = 500; code < 600; code++) {
            executions++;
            when(responseMetaData.status()).thenReturn(HttpResponseStatus.of(code, "any"));
            getExtractor(isServer).extract(spanStatusBuilder, requestInfo, responseMetaData, null);
        }
        verify(spanStatusBuilder, times(executions)).setStatus(StatusCode.ERROR);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: isServer={0}")
    @ValueSource(booleans = {true, false})
    void testStatusUnknown(boolean isServer) {
        RequestInfo requestInfo = new RequestInfo(requestMetaData, null);
        when(responseMetaData.status()).thenReturn(HttpResponseStatus.of(600, "any"));
        getExtractor(isServer).extract(spanStatusBuilder, requestInfo, responseMetaData, null);
        verify(spanStatusBuilder, times(0)).setStatus(any());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: isServer={0}")
    @ValueSource(booleans = {true, false})
    void testExceptionError(boolean isServer) {
        RequestInfo requestInfo = new RequestInfo(requestMetaData, null);
        getExtractor(isServer).extract(spanStatusBuilder, requestInfo, responseMetaData,
            new RuntimeException());
        verify(spanStatusBuilder).setStatus(StatusCode.ERROR);
    }

    private static HttpSpanStatusExtractor getExtractor(boolean isServer) {
        return isServer ? HttpSpanStatusExtractor.SERVER_INSTANCE :
                HttpSpanStatusExtractor.CLIENT_INSTANCE;
    }
}
