/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.protoc;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.protoc.test.conflict.rpc.TestConflictRpc.Test.TestService;
import io.servicetalk.grpc.protoc.test.conflict.rpc.TestConflictRpc.TestRpc.TestRpcService;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.grpc.protoc.test.conflict.rpc.TestConflictRpc.TestReply;

class TestConflictRpc {
    @Test
    void conflictRpcServiceGenerated() throws ExecutionException, InterruptedException {
        TestService service = (ctx, request) -> Single.succeeded(TestReply.newBuilder().build());
        TestRpcService rpcService = (ctx, request) -> Single.succeeded(TestReply.newBuilder().build());

        service.closeAsync().toFuture().get();
        rpcService.closeAsync().toFuture().get();
    }
}
