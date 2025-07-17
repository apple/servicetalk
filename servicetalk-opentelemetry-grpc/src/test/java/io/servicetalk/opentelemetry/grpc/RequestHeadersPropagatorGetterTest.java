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

import io.opentelemetry.context.propagation.TextMapGetter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestHeadersPropagatorGetterTest {
    @Test
    void shouldGetAllKeys() {
        final TextMapGetter<GrpcRequestInfo> getter = RequestHeadersPropagatorGetter.INSTANCE;
        HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        GrpcRequestInfo grpcRequestInfo = getGrpcRequestInfo(httpHeaders);
        httpHeaders.set("a", "1");
        httpHeaders.set("b", "2");

        final Iterable<String> keys = getter.keys(grpcRequestInfo);

        assertThat(keys).containsAll(Arrays.asList("a", "b"));
    }

    @Test
    void shouldReturnNullWhenThereIsNotKeyInCarrier() {

        final TextMapGetter<GrpcRequestInfo> getter = RequestHeadersPropagatorGetter.INSTANCE;

        HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        GrpcRequestInfo grpcRequestInfo = getGrpcRequestInfo(httpHeaders);

        assertThat(getter.get(grpcRequestInfo, "c")).isNull();
    }

    @Test
    void shouldReturnValueWhenThereIsAKeyInCarrierCaseInsensitive() {

        final TextMapGetter<GrpcRequestInfo> getter = RequestHeadersPropagatorGetter.INSTANCE;

        HttpHeaders httpHeaders = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        httpHeaders.set("A", "1");
        GrpcRequestInfo grpcRequestInfo = getGrpcRequestInfo(httpHeaders);

        assertThat(getter.get(grpcRequestInfo, "A")).isEqualTo("1");
    }

    @Test
    void shouldReturnNullWhenCarrierIsNull() {
        final TextMapGetter<GrpcRequestInfo> getter = RequestHeadersPropagatorGetter.INSTANCE;

        assertThat(getter.get(null, "A")).isNull();
    }

    private static GrpcRequestInfo getGrpcRequestInfo(HttpHeaders httpHeaders) {
        HttpRequestMetaData requestMetaData = mock(HttpRequestMetaData.class);
        ConnectionInfo connectionInfo = mock(ConnectionInfo.class);
        GrpcRequestInfo grpcRequestInfo = new GrpcRequestInfo(requestMetaData, connectionInfo);
        when(requestMetaData.headers()).thenReturn(httpHeaders);
        return grpcRequestInfo;
    }
}
