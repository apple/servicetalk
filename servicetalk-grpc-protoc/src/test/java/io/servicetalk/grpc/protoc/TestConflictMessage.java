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
package io.servicetalk.grpc.protoc;

import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictMessageOuterClass.TestConflictMessageService.TestConflictMessageServiceService;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictMessageOuterClass.TestConflictResp;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictMessageService2.TestConflictMessageService2Service;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictMessageService3.TestConflictMessageService3Service;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictResp2;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictResp3;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Single.succeeded;

class TestConflictMessage {
    @Test
    void messageConflict() throws ExecutionException, InterruptedException {
        ((TestConflictMessageServiceService) (ctx, request) -> succeeded(TestConflictResp.newBuilder().build()))
                .closeAsync().toFuture().get();
    }

    @Test
    void message2Conflict() throws ExecutionException, InterruptedException {
        ((TestConflictMessageService2Service) (ctx, request) -> succeeded(TestConflictResp2.newBuilder().build()))
                .closeAsync().toFuture().get();
    }

    @Test
    void message3Conflict() throws ExecutionException, InterruptedException {
        ((TestConflictMessageService3Service) (ctx, request) -> succeeded(TestConflictResp3.newBuilder().build()))
                .closeAsync().toFuture().get();
    }
}
