/*
 * Copyright © 2020, 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictMessageService2.TestConflictMessageService2Service;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictMessageService3.TestConflictMessageService3Service;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictResp2;
import io.servicetalk.grpc.protoc.test.conflict.message.TestConflictResp3;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService.TestConflict.TestConflictService0;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService4OuterClass.TestConflictService4.TestConflictService4Service;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService4OuterClass.TestConflictService4Resp;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService5.TestConflictService50.TestConflictService5Service;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService5.testConflictService5Req;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService5.testConflictService5Resp;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService5.test_conflict_service_5_req;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService5.test_conflict_service_5_resp;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictServiceOuterClassOuterClass.TestConflictServiceOuterClass.TestConflictServiceOuterClassService;
import io.servicetalk.grpc.protoc.test.conflict.service.TestConflictServiceOuterClassOuterClass.TestConflictServiceOuterServiceResp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.protoc.test.conflict.service.TestConflictService.TestReply;

class TestConflictService {
    @Test
    void conflictServiceGenerated() throws ExecutionException, InterruptedException {
        TestConflictService0 service = (ctx, request) -> succeeded(TestReply.newBuilder().build());
        service.closeAsync().toFuture().get();
    }

    @Test
    void conflict2ServiceGenerated() throws ExecutionException, InterruptedException {
        ((TestConflictMessageService2Service) (ctx, request) -> succeeded(TestConflictResp2.newBuilder().build()))
                .closeAsync().toFuture().get();
    }

    @Test
    void conflict3ServiceGenerated() throws ExecutionException, InterruptedException {
        ((TestConflictMessageService3Service) (ctx, request) -> succeeded(TestConflictResp3.newBuilder().build()))
                .closeAsync().toFuture().get();
    }

    @Test
    void conflict4ServiceGenerated() throws ExecutionException, InterruptedException {
        ((TestConflictService4Service) (ctx, request) -> succeeded(TestConflictService4Resp.newBuilder().build()))
                .closeAsync().toFuture().get();
    }

    @Test
    void conflict5ServiceGenerated() throws ExecutionException, InterruptedException {
        new TestConflictService5Service() {
            @Override
            public Single<test_conflict_service_5_resp> doNothing2(
                    final GrpcServiceContext ctx, final test_conflict_service_5_req request) {
                return succeeded(test_conflict_service_5_resp.newBuilder().build());
            }

            @Override
            public Single<testConflictService5Resp> doNothing(
                    final GrpcServiceContext ctx, final testConflictService5Req request) {
                return succeeded(testConflictService5Resp.newBuilder().build());
            }
        }.closeAsync().toFuture().get();
    }

    @Test
    void conflictOuterServiceGenerated() throws ExecutionException, InterruptedException {
        ((TestConflictServiceOuterClassService) (ctx, request) ->
                succeeded(TestConflictServiceOuterServiceResp.newBuilder().build())).closeAsync().toFuture().get();
    }
}
