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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.TestResponse;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterService;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.customtransport.Utils.newResp;

final class AsyncUserService implements TesterService {
    @Override
    public Single<TestResponse> test(final GrpcServiceContext ctx, final TestRequest request) {
        return succeeded(newResp("hello " + request.getName()));
    }

    @Override
    public Publisher<TestResponse> testBiDiStream(final GrpcServiceContext ctx,
                                                  final Publisher<TestRequest> request) {
        return request.map(req -> newResp("hello " + req.getName()));
    }

    @Override
    public Publisher<TestResponse> testResponseStream(final GrpcServiceContext ctx,
                                                      final TestRequest request) {
        return from(newResp("hello " + request.getName() + "1"), newResp("hello " + request.getName() + "2"));
    }

    @Override
    public Single<TestResponse> testRequestStream(final GrpcServiceContext ctx,
                                                  final Publisher<TestRequest> request) {
        return request.collect(StringBuilder::new, (sb, req) -> sb.append(req.getName()).append(", "))
                .map(sb -> newResp("hello " + sb));
    }
}
