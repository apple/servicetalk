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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.opentelemetry.grpc.CapturedGrpcMetadataUtil.lowercase;
import static io.servicetalk.opentelemetry.grpc.CapturedGrpcMetadataUtil.requestAttributeKey;
import static io.servicetalk.opentelemetry.grpc.CapturedGrpcMetadataUtil.responseAttributeKey;

class GrpcAttributesExtractor
    implements AttributesExtractor<GrpcRequestInfo, GrpcTelemetryStatus> {

    // copied from RpcIncubatingAttributes
    private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE =
        AttributeKey.longKey("rpc.grpc.status_code");

    private final ServicetalkGrpcAttributesGetter getter;
    private final List<String> capturedRequestMetadata;
    private final List<String> capturedResponseMetadata;

    GrpcAttributesExtractor(ServicetalkGrpcAttributesGetter getter,
                            List<String> capturedRequestMetadata,
                            List<String> capturedResponseMetadata) {
        this.getter = getter;
        this.capturedRequestMetadata = lowercase(capturedRequestMetadata);
        this.capturedResponseMetadata = lowercase(capturedResponseMetadata);
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, GrpcRequestInfo requestInfo) {
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, GrpcRequestInfo requestInfo,
                      @Nullable GrpcTelemetryStatus status, @Nullable Throwable error) {
        if (status != null) {
            attributes.put(RPC_GRPC_STATUS_CODE, status.getGrpcStatus().code().value());
        }
        for (String key : capturedRequestMetadata) {
            List<String> value = getter.metadataValue(requestInfo.getMetadata(), key);
            if (!value.isEmpty()) {
                attributes.put(requestAttributeKey(key), value);
            }
        }
        if (status != null && status.getResponseMetaData() != null) {
            for (String key : capturedResponseMetadata) {
                List<String> value = getter.metadataValue(status.getResponseMetaData(), key);
                if (!value.isEmpty()) {
                    attributes.put(responseAttributeKey(key), value);
                }
            }
        }
    }
}
