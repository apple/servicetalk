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
package io.servicetalk.utils.internal;

import static java.lang.String.format;

/**
 * Exception that clarifies an illegal character and expected values.
 */
public final class IllegalCharacterException extends IllegalArgumentException {
    private static final long serialVersionUID = 5109746801766842145L;

    /**
     * Creates a new instance.
     *
     * @param value value of the character
     */
    public IllegalCharacterException(final byte value) {
        super(format("'%1$c' (0x%1$02X)", value & 0xff));
    }

    /**
     * Creates a new instance.
     *
     * @param value value of the character
     * @param expected definition of expected value(s)
     */
    public IllegalCharacterException(final byte value, final String expected) {
        super(format("'%1$c' (0x%1$02X), expected [%2$s]", value & 0xff, expected));
    }
}
