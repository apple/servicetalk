/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.customtransport;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.BlockingTesterService;

import static io.servicetalk.grpc.customtransport.Utils.newResp;

final class BlockingUserService implements BlockingTesterService {
    @Override
    public TestResponse test(final GrpcServiceContext ctx, final TestRequest request) throws Exception {
        return TestResponse.newBuilder().setMessage("hello " + request.getName()).build();
    }

    @Override
    public void testBiDiStream(final GrpcServiceContext ctx, final BlockingIterable<TestRequest> request,
                               final GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
        for (TestRequest req : request) {
            responseWriter.write(newResp("hello " + req.getName()));
        }
        responseWriter.close();
    }

    @Override
    public void testResponseStream(final GrpcServiceContext ctx, final TestRequest request,
                                   final GrpcPayloadWriter<TestResponse> responseWriter) throws Exception {
        responseWriter.write(newResp("hello " + request.getName() + "1"));
        responseWriter.write(newResp("hello " + request.getName() + "2"));
        responseWriter.close();
    }

    @Override
    public TestResponse testRequestStream(final GrpcServiceContext ctx, final BlockingIterable<TestRequest> request)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        for (TestRequest req : request) {
            sb.append(req.getName()).append(", ");
        }
        return newResp("hello " + sb);
    }
}
