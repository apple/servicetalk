/*
 * Copyright © 2020, 2026 Apple Inc. and the ServiceTalk project authors
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

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static io.servicetalk.grpc.protoc.FileDescriptor.insertionPoint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsertionPointTest {

    @Test
    void insertionPointExistsInMultiFiles() throws IOException {
        List<CodeGeneratorResponse.File> files = generate("test_multi.proto");
        assertThat(files, hasSize(2));

        assertThat(files.get(0).getContent(), containsString(insertionPoint("test.multi.Tester")));
        assertThat(files.get(1).getContent(), containsString(insertionPoint("test.multi.Tester2")));
    }

    @Test
    void insertionPointExistsInSingleFile() throws IOException {
        List<CodeGeneratorResponse.File> files = generate("test_single.proto");
        assertThat(files, hasSize(3));
        assertThat(files.get(1).getContent(), containsString(insertionPoint("test.single.Greeter")));
        assertThat(files.get(2).getContent(), containsString(insertionPoint("test.single.Fareweller")));
    }

    private static List<CodeGeneratorResponse.File> generate(String file) throws IOException {
        final CodeGeneratorRequest.Builder reqBuilder = CodeGeneratorRequest.newBuilder();
        reqBuilder.addAllProtoFile(getFileDescriptorSet().getFileList());
        reqBuilder.addFileToGenerate(file);
        CodeGeneratorResponse codeGeneratorResponse = executePlugin(reqBuilder.build());
        return codeGeneratorResponse.getFileList();
    }

    /**
     * Executes protoc servicetalk plugin
     * @param request generator request
     * @return generator response
     * @throws IOException throws exception if it fails to get hold of descriptor set
     */
    private static CodeGeneratorResponse executePlugin(CodeGeneratorRequest request) throws IOException {
        // make stdin with request
        ByteArrayOutputStream tempCollector = new ByteArrayOutputStream();
        request.writeTo(tempCollector);
        ByteArrayInputStream stdinWithRequest = new ByteArrayInputStream(tempCollector.toByteArray());

        // hold stdout into this after plugin execution
        ByteArrayOutputStream stdoutWithResponse = new ByteArrayOutputStream();

        InputStream stdin = System.in;
        PrintStream stdout = System.out;
        try {
            // prepare stdin and stdout for the plugin execution
            System.setIn(stdinWithRequest);
            System.setOut(new PrintStream(stdoutWithResponse, true, StandardCharsets.UTF_8.name()));
            // execute plugin
            Main.main();
            return CodeGeneratorResponse.parseFrom(stdoutWithResponse.toByteArray());
        } finally {
            // restore
            System.setIn(stdin);
            System.setOut(stdout);
        }
    }

    /**
     * This descriptor file is expected to be generated at build time by the protoc plugin
     * @return descriptor fileset
     * @throws IOException throws exception if it fails to locate descriptor set on file system
     */
    static DescriptorProtos.FileDescriptorSet getFileDescriptorSet() throws IOException {
        String baseDir = System.getProperty("generatedFilesBaseDir",
                "servicetalk-grpc-protoc/build/generated/source/proto");
        File descriptorSet = new File(baseDir + "/test/descriptor_set.desc");
        assertTrue(descriptorSet.exists());
        byte[] data = Files.readAllBytes(descriptorSet.toPath());
        return DescriptorProtos.FileDescriptorSet.parseFrom(data);
    }
}
