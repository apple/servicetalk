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

import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributesGetter;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

final class ServicetalkGrpcAttributesGetter implements RpcAttributesGetter<GrpcRequestInfo> {
    static final ServicetalkGrpcAttributesGetter INSTANCE = new ServicetalkGrpcAttributesGetter();

    private ServicetalkGrpcAttributesGetter() {
    }

    @Override
    public String getSystem(GrpcRequestInfo httpRequestMetaData) {
        return "grpc";
    }

    @Nullable
    @Override
    public String getService(GrpcRequestInfo requestInfo) {
        String fullMethodName = requestInfo.getMetadata().path();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return null;
        }
        return fullMethodName.substring(1, slashIndex);
    }

    @Nullable
    @Override
    public String getMethod(GrpcRequestInfo request) {
        String fullMethodName = request.getMetadata().path();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return null;
        }
        return fullMethodName.substring(slashIndex + 1);
    }

    public List<String> metadataValue(HttpRequestMetaData metadata, @Nullable String key) {
        if (key == null || key.isEmpty()) {
            return Collections.emptyList();
        }

        CharSequence value = metadata.headers().get(key);

        if (value == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(value.toString());
    }

    public List<String> metadataValue(HttpResponseMetaData metadata, String key) {
        if (key == null || key.isEmpty()) {
            return Collections.emptyList();
        }

        CharSequence value = metadata.headers().get(key);

        if (value == null) {
            return Collections.emptyList();
        }

        return Collections.singletonList(value.toString());
    }
}
