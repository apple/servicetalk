/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.grpc.netty.TesterProto.Tester;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.servicetalk.grpc.protoc.Words.Blocking;
import static io.servicetalk.grpc.protoc.Words.Rpc;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class GeneratorTest {

    @Test
    void testGeneratedFunctionalInterfaces() {

        final List<Class<?>> generatedRpcInterfaces = stream(Tester.class.getDeclaredClasses())
                .filter(declaredClass -> declaredClass.getAnnotation(FunctionalInterface.class) != null
                        && !declaredClass.getSimpleName().startsWith(Blocking)
                        && declaredClass.getSimpleName().endsWith(Rpc))
                .collect(toList());

        final List<Class<?>> generatedBlockingRpcInterfaces = stream(Tester.class.getDeclaredClasses())
                .filter(declaredClass -> declaredClass.getAnnotation(FunctionalInterface.class) != null
                        && declaredClass.getSimpleName().startsWith(Blocking)
                        && declaredClass.getSimpleName().endsWith(Rpc))
                .collect(toList());

        assertThat(generatedRpcInterfaces.size(), is(4));
        assertThat(generatedRpcInterfaces.size(), is(generatedBlockingRpcInterfaces.size()));
    }
}
