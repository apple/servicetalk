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

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableList;

/**
 * A utility class for creating and caching attribute keys for captured gRPC request metadata.
 */
final class CapturedGrpcMetadataUtil {
    private static final String RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX =
        "rpc.grpc.request.metadata.";
    private static final String RPC_RESPONSE_METADATA_KEY_ATTRIBUTE_PREFIX =
        "rpc.grpc.response.metadata.";
    private static final ConcurrentMap<String, AttributeKey<List<String>>> requestKeysCache =
        new ConcurrentHashMap<>();

    private CapturedGrpcMetadataUtil() {}

    static List<String> lowercase(List<String> names) {
        return unmodifiableList(
            names.stream().map(s -> s.toLowerCase(Locale.ENGLISH)).collect(Collectors.toList()));
    }

    static AttributeKey<List<String>> requestAttributeKey(String metadataKey) {
        return requestKeysCache.computeIfAbsent(
            metadataKey, CapturedGrpcMetadataUtil::createRequestKey);
    }

    private static AttributeKey<List<String>> createRequestKey(String metadataKey) {
        return AttributeKey.stringArrayKey(RPC_REQUEST_METADATA_KEY_ATTRIBUTE_PREFIX + metadataKey);
    }

    static AttributeKey<List<String>> responseAttributeKey(String metadataKey) {
        return requestKeysCache.computeIfAbsent(
            metadataKey, CapturedGrpcMetadataUtil::createResponseKey);
    }

    private static AttributeKey<List<String>> createResponseKey(String metadataKey) {
        return AttributeKey.stringArrayKey(RPC_RESPONSE_METADATA_KEY_ATTRIBUTE_PREFIX + metadataKey);
    }
}
