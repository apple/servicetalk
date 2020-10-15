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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;

final class StringUtils {
    private StringUtils() {
        // no instances
    }

    /**
     * Sanitize a string to conform to java identifier standards.
     *
     * @param v The un-sanitized String.
     * @param firstToLower if {@code true} the first character (if ASCII) will be forced to lower case.
     * otherwise the first character (if ASCII) will be forced to upper case.
     * @return The sanitized String.
     */
    static String sanitizeIdentifier(final String v, final boolean firstToLower) {
        if (isNullOrEmpty(v)) {
            throw new IllegalArgumentException("java identifier must have length >= 1");
        }
        final StringBuilder sb = new StringBuilder(v.length());
        sb.append(firstToLower ? toLowerCase(v.charAt(0)) : toUpperCase(v.charAt(0)));
        boolean afterUnderscore = false;
        for (int i = 1; i < v.length(); ++i) {
            final char c = v.charAt(i);
            if (c == '_') {
                afterUnderscore = true;
            } else {
                sb.append(afterUnderscore ? toUpperCase(c) : c);
                afterUnderscore = false;
            }
        }
        return sb.toString();
    }

    static boolean isNotNullNorEmpty(@Nullable final String v) {
        return v != null && !v.isEmpty();
    }

    static boolean isNullOrEmpty(@Nullable final String v) {
        return v == null || v.isEmpty();
    }

    /**
     * Parse options which are defined to be a comma separated list passed by protoc to the plugin.
     * <pre>
     *     protoc --plug_out=enable_bar:outdir --plug_opt=enable_baz
     *     protoc --plug_out=enable_bar,enable_baz,mykey=myvalue:outdir
     * </pre>
     * @param parameters The options as specified by
     * <a href="
     * https://developers.google.com/protocol-buffers/docs/reference/cpp/google.protobuf.compiler.command_line_interface
     * ">protoc options</a>
     * and
     * <a href="
     * https://github.com/google/protobuf-gradle-plugin#configure-what-to-generate
     * ">protobuf-gradle-plugin options</a>.
     * @return A map of the options parsed into &lt;key,value&gt; pairs.
     */
    static Map<String, String> parseOptions(String parameters) {
        Map<String, String> options = new HashMap<>();
        int begin = 0;
        while (begin < parameters.length() && begin >= 0) {
            int delim = parameters.indexOf(',', begin);
            final String option;
            if (delim > begin) {
                option = parameters.substring(begin, delim);
                begin = delim + 1;
            } else {
                option = parameters.substring(begin);
                begin = -1;
            }
            int equals = option.indexOf('=');
            if (equals > 0) {
                options.put(option.substring(0, equals), option.substring(equals + 1));
            } else {
                options.put(option, null);
            }
        }
        return options;
    }
}
