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
package io.servicetalk.grpc.api;

import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.HttpResponseMetaData;

/**
 * The response for streaming use-cases that provides ability to defer sending response meta-data and write the payload
 * to an {@link GrpcPayloadWriter}.
 *
 * @param <T> the type or response message
 */
public interface BlockingStreamingGrpcServerResponse<T> {

    /**
     * A response context that will be translated into {@link HttpResponseMetaData#context()}.
     * <p>
     * This is an equivalent of {@link GrpcMetadata#responseContext()}.
     *
     * @return a response context that will be translated into {@link HttpResponseMetaData#context()}
     * @throws IllegalStateException if {@link #sendMetaData()} is already invoked
     * @see GrpcMetadata#responseContext()
     */
    ContextMap context();

    /**
     * Sends the response meta-data and returns a {@link GrpcPayloadWriter} to continue writing the response messages.
     * <p>
     * <b>Note:</b> calling any other method on this class after calling this method is not allowed. Any modifications
     * to the meta-data won't be visible after this method is invoked.
     *
     * @return {@link GrpcPayloadWriter} to write response messages
     */
    GrpcPayloadWriter<T> sendMetaData();
}
