/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.internal.build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class ExecutableBuilder {

    private ExecutableBuilder() {
        // no instances
    }

    public static void buildUnixExecutable(String uberJarName, File outputFile) throws IOException {
        prepareOutputFile(outputFile);
        try(FileOutputStream execOutputStream = new FileOutputStream(outputFile)) {
            execOutputStream.write(("#!/bin/sh\n" +
                    "pushd $(dirname \"$0\") > /dev/null\n" +
                    "exec java -jar " + uberJarName + " \"$@\"\n" +
                    "popd > /dev/null\n").getBytes(StandardCharsets.US_ASCII));
        }
        finalizeOutputFile(outputFile);
    }

    public static void buildWindowsExecutable(String uberJarName, File outputFile) throws IOException {
        prepareOutputFile(outputFile);
        try(FileOutputStream execOutputStream = new FileOutputStream(outputFile)) {
            execOutputStream.write(("@ECHO OFF\r\n" +
                    "pushd %~dp0\r\n" +
                    "java -jar " + uberJarName + " %*\r\n" +
                    "popd\r\n").getBytes(StandardCharsets.US_ASCII));
        }
        finalizeOutputFile(outputFile);
    }

    public static String addExecutablePostFix(String rawName, boolean isWindows) {
        return rawName + (isWindows ? ".bat" : ".sh");
    }

    private static void prepareOutputFile(File outputFile) throws IOException {
        if (!outputFile.exists()) {
            if (!outputFile.getParentFile().isDirectory() && !outputFile.getParentFile().mkdirs()) {
                throw new IOException("unable to make directories for file: " + outputFile.getCanonicalPath());
            }
        } else {
            // Clear the file's contents
            new PrintWriter(outputFile).close();
        }
    }

    private static void finalizeOutputFile(File outputFile) throws IOException {
        if (!outputFile.setExecutable(true)) {
            outputFile.delete();
            throw new IOException("unable to set file as executable: " + outputFile.getCanonicalPath());
        }
    }
}
