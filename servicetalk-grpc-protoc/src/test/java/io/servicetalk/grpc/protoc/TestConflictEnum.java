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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnum2OuterClass.TestConflictEnum2Resp;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnum2OuterClass.TestConflictEnum2Service.TestConflictEnum2ServiceService;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnum3Resp;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnum3Service.TestConflictEnum3ServiceService;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnumInsideOuterClass.TestConflictEnumInside.TestConflictEnumInsideService;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnumInsideOuterClass.TestConflictEnumInsideResp;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnumReq;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnumResp;
import io.servicetalk.grpc.protoc.test.conflict.enums.TestConflictEnumService.TestConflictEnumServiceService;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Single.succeeded;

class TestConflictEnum {
    @Test
    void enumConflict() throws ExecutionException, InterruptedException {
        new TestConflictEnumServiceService() {
            @Override
            public Single<TestConflictEnumResp> doNothingRpc(
                    final GrpcServiceContext ctx, final TestConflictEnumReq request) {
                return succeeded(TestConflictEnumResp.newBuilder().build());
            }

            @Override
            public Single<TestConflictEnumResp> doNothing(
                    final GrpcServiceContext ctx, final TestConflictEnumReq request) {
                return succeeded(TestConflictEnumResp.newBuilder().build());
            }
        }.closeAsync().toFuture().get();
    }

    @Test
    void enum2Conflict() throws ExecutionException, InterruptedException {
        ((TestConflictEnum2ServiceService) (ctx, request) -> succeeded(TestConflictEnum2Resp.newBuilder().build()))
                .closeAsync().toFuture().get();
    }

    @Test
    void enum3Conflict() throws ExecutionException, InterruptedException {
        ((TestConflictEnum3ServiceService) (ctx, request) -> succeeded(TestConflictEnum3Resp.newBuilder().build()))
                .closeAsync().toFuture().get();
    }

    @Test
    void enumInsideConflict() throws ExecutionException, InterruptedException {
        ((TestConflictEnumInsideService) (ctx, request) -> succeeded(TestConflictEnumInsideResp.newBuilder().build()))
                .closeAsync().toFuture().get();
    }
}
