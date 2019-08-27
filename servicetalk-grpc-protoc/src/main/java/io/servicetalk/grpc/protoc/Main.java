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

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.squareup.javapoet.ClassName;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest.parseFrom;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * This class implements the
 * <a href="https://developers.google.com/protocol-buffers/docs/reference/cpp/google.protobuf.compiler.plugin.pb">
 * plugin.proto</a>
 * interface to produce ServiceTalk code corresponding to .proto service definitions.
 */
public final class Main {
    private Main() {
        // no instances
    }

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
            final StringWriter sw = new StringWriter(1024);
            sw.append("ServiceTalk code generation failed: ");
            try (PrintWriter pw = new PrintWriter(sw)) {
                t.printStackTrace(pw);
            }
            return CodeGeneratorResponse.newBuilder().setError(sw.toString()).build();
        }
    }

    private static CodeGeneratorResponse generate(final CodeGeneratorRequest request) {
        final CodeGeneratorResponse.Builder responseBuilder = CodeGeneratorResponse.newBuilder();

        final Set<String> filesToGenerate = new HashSet<>(request.getFileToGenerateList());

        final List<FileDescriptor> fileDescriptors =
                request.getProtoFileList().stream().map(FileDescriptor::new).collect(toList());

        final Map<String, ClassName> messageTypesMap = fileDescriptors.stream()
                .map(FileDescriptor::messageTypesMap)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(toMap(Entry::getKey, Entry::getValue));

        fileDescriptors.stream()
                .filter(f -> filesToGenerate.contains(f.protoFileName()))
                .forEach(f -> {
                    final Generator generator = new Generator(f, messageTypesMap);
                    f.protoServices().forEach(generator::generate);
                    f.writeTo(responseBuilder);
                });

        return responseBuilder.build();
    }
}
