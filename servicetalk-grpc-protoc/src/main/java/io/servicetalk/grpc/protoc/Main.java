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

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest.parseFrom;

/**
 * This class implements the
 * <a href="https://developers.google.com/protocol-buffers/docs/reference/cpp/google.protobuf.compiler.plugin.pb">plugin.proto</a>
 * interface to produce ServiceTalk code corresponding to .proto service definitions.
 */
public final class Main {
    /**
     * Program entry point. It is expected a {@link CodeGeneratorRequest} will be read from stdin,
     * and a {@link CodeGeneratorResponse} will be written to stdout.
     *
     * @param args the program arguments
     * @throws IOException if an exception occurs while parsing input from stdin
     */
    public static void main(final String... args) throws IOException {
        safeGenerate(parseFrom(System.in)).writeTo(System.out);
    }

    private static CodeGeneratorResponse safeGenerate(final CodeGeneratorRequest request) {
        try {
            return generate(request);
        } catch (final Throwable t) {
            return CodeGeneratorResponse.newBuilder().setError("ServiceTalk code generation failed: " + t).build();
        }
    }

    private static CodeGeneratorResponse generate(final CodeGeneratorRequest request) {
        final CodeGeneratorResponse.Builder responseBuilder = CodeGeneratorResponse.newBuilder();

        final Set<String> filesToGenerate = new HashSet<>(request.getFileToGenerateList());

        for (final FileDescriptorProto protoFile : request.getProtoFileList()) {
            final FileGenerator fileGenerator;
            if (protoFile.hasOptions()) {
                final FileOptions fileOptions = protoFile.getOptions();
                fileGenerator = new FileGenerator(protoFile.getName(),
                        protoFile.hasPackage() ? protoFile.getPackage() : null,
                        protoFile.getServiceCount(),
                        fileOptions.hasDeprecated() && fileOptions.getDeprecated(),
                        fileOptions.hasJavaMultipleFiles() && fileOptions.getJavaMultipleFiles(),
                        fileOptions.hasJavaPackage() ? fileOptions.getJavaPackage() : null,
                        fileOptions.hasJavaOuterClassname() ? fileOptions.getJavaOuterClassname() : null);
            } else {
                fileGenerator = new FileGenerator(protoFile.getName(),
                        protoFile.hasPackage() ? protoFile.getPackage() : null,
                        protoFile.getServiceCount());
            }

            // Generate code for this file
            if (filesToGenerate.contains(protoFile.getName())) {
                for (final ServiceDescriptorProto serviceProto : protoFile.getServiceList()) {
                    final TypeSpec.Builder serviceClassBuilder = fileGenerator.newServiceClassBuilder(serviceProto);
                    // TODO(david) generate methods in serviceClassBuilder
                }
            }

            fileGenerator.writeTo(responseBuilder);
        }

        return responseBuilder.build();
    }
}
