/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.grpc;

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.ConnectionInfo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicetalkGrpcAttributesGetterTest {
    @Test
    void getSystem() {
        HttpRequestMetaData requestMetaData = mock(HttpRequestMetaData.class);
        ServicetalkGrpcAttributesGetter attributesGetter = ServicetalkGrpcAttributesGetter.INSTANCE;
        assertThat(attributesGetter.getSystem(getGrpcRequestInfo(requestMetaData))).isEqualTo("grpc");
    }

    @Test
    void getService() {
        HttpRequestMetaData requestMetaData = mock(HttpRequestMetaData.class);
        when(requestMetaData.path()).thenReturn("/com.apple.schema.greeting.JobService/create");
        ServicetalkGrpcAttributesGetter attributesGetter = ServicetalkGrpcAttributesGetter.INSTANCE;
        assertThat(attributesGetter.getService(getGrpcRequestInfo(requestMetaData)))
            .isEqualTo("com.apple.schema.greeting.JobService");
    }

    @Test
    void getMethod() {
        HttpRequestMetaData requestMetaData = mock(HttpRequestMetaData.class);
        when(requestMetaData.path()).thenReturn("/com.apple.schema.greeting.JobService/create");
        ServicetalkGrpcAttributesGetter attributesGetter = ServicetalkGrpcAttributesGetter.INSTANCE;
        assertThat(attributesGetter.getMethod(getGrpcRequestInfo(requestMetaData)))
            .isEqualTo("create");
    }

    private static GrpcRequestInfo getGrpcRequestInfo(HttpRequestMetaData requestMetaData) {
        HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        GrpcRequestInfo grpcRequestInfo = new GrpcRequestInfo(requestMetaData, connectionInfo);
        when(requestMetaData.headers()).thenReturn(httpHeaders);
        return grpcRequestInfo;
    }
}
