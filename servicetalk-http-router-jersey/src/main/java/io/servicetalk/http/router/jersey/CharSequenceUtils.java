/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

/**
 * CharSequence helpers.
 */
final class CharSequenceUtils {
    private CharSequenceUtils() {
        // no instances
    }

    static CharSequence asCharSequence(final Object o) {
        return o instanceof CharSequence ? (CharSequence) o : o.toString();
    }

    static CharSequence ensureNoLeadingSlash(final CharSequence cs) {
        if (cs.length() == 0 || cs.charAt(0) != '/') {
            return cs;
        }
        for (int i = 1; i < cs.length(); i++) {
            if (cs.charAt(i) != '/') {
                return cs.subSequence(i, cs.length());
            }
        }
        return "";
    }

    static CharSequence ensureTrailingSlash(final CharSequence cs) {
        if (cs.length() > 0 && cs.charAt(cs.length() - 1) == '/') {
            return cs;
        }
        return new StringBuilder(cs).append('/');
    }
}
