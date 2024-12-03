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

import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;

interface GenerationContext {
    /**
     * Return a canonical and unique type name based upon the provided type name. The returned name will not conflict
     * with any other identically named types in the same context.
     *
     * @param name The Java type name to deconflict
     * @return The deconflicted, possibly unchanged, Java type name.
     */
    String deconflictJavaTypeName(String name);

    /**
     * Return a qualified, canonical, and unique type name based upon the provided type name. The returned name will not
     * conflict with any other identically named types in the same context.
     *
     * @param outerClassName The outer class name that contains this type. This will be used to generate a
     * more fully qualified return value.
     * @param name The Java type name to deconflict
     * @return Deconflicted, possibly unchanged, Java type name that is qualified with the outer java
     * package+typename+{@code outerClassName} scope.
     */
    String deconflictJavaTypeName(String outerClassName, String name);

    /**
     * Create and return the builder for a service class described by the provided descriptor. The builder will be
     * registered for the destination file and the generator responsible for filling in the builder will
     *
     * @param serviceProto The service descriptor being created.
     * @return a new builder for the service class
     */
    ServiceClassBuilder newServiceClassBuilder(ServiceDescriptorProto serviceProto);

    /**
     * Get the <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC H2 path</a> for a method.
     * @param serviceProto protobuf descriptor for the service.
     * @param methodProto protobuf descriptor for the method.
     * @return the <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC H2 path</a> for a
     * method.
     */
    String methodPath(ServiceDescriptorProto serviceProto, MethodDescriptorProto methodProto);
}
