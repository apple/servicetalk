/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.utils.internal.IllegalCharacterException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static java.lang.System.clearProperty;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DefaultHttpHeadersTest extends AbstractHttpHeadersTest {

    private static final String TOKEN_EXPECTED = ", expected: TCHAR (any VCHAR except delimiters)";
    private static final String FIELD_VALUE_EXPECTED =
            ", expected: (VCHAR / obs-text) [ 1*(SP / HTAB) (VCHAR / obs-text) ]";
    private static final String SECRET = "s3cr3t";
    private static final String INVALID_VALUE = SECRET + "\nvalue";
    private static final String INVALID_VALUE_MESSAGE =
            "0x0a (LF) at index=6 of length=12 in value of header 'x-api-key'" + FIELD_VALUE_EXPECTED;

    @Test
    void defaultFactoryValidatesValues() {
        assertThat(DefaultHttpHeadersFactory.INSTANCE.validateValues(), is(true));
        assertThrows(IllegalArgumentException.class,
                () -> DefaultHttpHeadersFactory.INSTANCE.newHeaders().add("name", "invalid\0value"));
    }

    @Test
    void explicitFactoryCanDisableValueValidation() {
        assertDoesNotThrow(() -> new DefaultHttpHeadersFactory(true, true, false).newHeaders()
                .add("name", "invalid\0value"));
    }

    @Test
    void temporaryDefaultValidateValuesPropertyCanDisableDefault() {
        final String oldValue = getProperty(DefaultHttpHeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY);
        try {
            clearProperty(DefaultHttpHeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY);
            assertThat(DefaultHttpHeadersFactory.defaultValidateValues(), is(true));

            setProperty(DefaultHttpHeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY, "false");
            assertThat(DefaultHttpHeadersFactory.defaultValidateValues(), is(false));
        } finally {
            if (oldValue == null) {
                clearProperty(DefaultHttpHeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY);
            } else {
                setProperty(DefaultHttpHeadersFactory.DEFAULT_VALIDATE_VALUES_PROPERTY, oldValue);
            }
        }
    }

    @Test
    void addHeadersValidatesNamesWhenSourceDoesNot() {
        DefaultHttpHeaders source = newHeaders(false, false);
        source.add("invalid name", "value");

        DefaultHttpHeaders dest = newHeaders(true, false);
        assertThrows(IllegalArgumentException.class, () -> dest.add(source));
    }

    @Test
    void addHeadersValidatesValuesWhenSourceDoesNot() {
        DefaultHttpHeaders source = newHeaders(false, false);
        source.add("name", "invalid\0value");

        DefaultHttpHeaders dest = newHeaders(false, true);
        assertThrows(IllegalArgumentException.class, () -> dest.add(source));
    }

    @Test
    void addHeadersSkipsNameValidationWhenSourceAlreadyValidates() {
        // Load an invalid name into a source that claims to validate names.
        DefaultHttpHeaders raw = newHeaders(false, false);
        raw.add("invalid name", "value");
        DefaultHttpHeaders source = newHeaders(true, false);
        source.putAll(raw, false, false);

        DefaultHttpHeaders dest = newHeaders(true, false);
        assertDoesNotThrow(() -> dest.add(source));
    }

    @Test
    void addHeadersSkipsValueValidationWhenSourceAlreadyValidates() {
        // Load an invalid value into a source that claims to validate values.
        DefaultHttpHeaders raw = newHeaders(false, false);
        raw.add("name", "invalid\0value");
        DefaultHttpHeaders source = newHeaders(false, true);
        source.putAll(raw, false, false);

        DefaultHttpHeaders dest = newHeaders(false, true);
        assertDoesNotThrow(() -> dest.add(source));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("mutators")
    void invalidHeaderValueMessage(String mutatorName, HeaderMutator mutator) {
        assertInvalidValueMessage(() -> mutator.apply(newHeaders(), "x-api-key", INVALID_VALUE));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("mutators")
    void invalidHeaderValueAsciiStringMessage(String mutatorName, HeaderMutator mutator) {
        assertInvalidValueMessage(() -> mutator.apply(newHeaders(), newAsciiString("x-api-key"),
                newAsciiString(INVALID_VALUE)));
    }

    @Test
    void invalidHeaderValueMessageWhenCopiedFromNonValidatingHeaders() {
        DefaultHttpHeaders source = newHeaders(false, false);
        source.add("x-api-key", INVALID_VALUE);
        assertInvalidValueMessage(() -> newHeaders(false, true).add(source));
    }

    @Test
    void invalidHeaderValueMessageForEveryIllegalCharacter() {
        final HttpHeaders headers = newHeaders();
        for (int c = 0; c <= 0xff; ++c) {
            if ((c >= 0x20 || c == '\t') && c != 0x7f) {
                continue;
            }
            final char illegal = (char) c;
            final IllegalCharacterException e = assertThrows(IllegalCharacterException.class,
                    () -> headers.add("x-api-key", SECRET + illegal));
            assertThat(e.getMessage(), containsString("at index=6 of length=7 in value of header 'x-api-key'"));
            assertNoSecretAndPrintable(e.getMessage());
        }
    }

    @Test
    void invalidHeaderValueMessageEscapesHeaderName() {
        // Name validation is disabled, so the name can contain characters that can break a log line:
        final IllegalCharacterException e = assertThrows(IllegalCharacterException.class,
                () -> newHeaders(false, true).add("x-api-key\r\n\u0141\\", INVALID_VALUE));
        assertThat(e.getMessage(), is("0x0a (LF) at index=6 of length=12 in value of header " +
                "'x-api-key\\x0d\\x0a\\u0141\\x5c'" + FIELD_VALUE_EXPECTED));
        assertNoSecretAndPrintable(e.getMessage());
    }

    @Test
    void validHeaderValuesAreAccepted() {
        final HttpHeaders headers = newHeaders();
        headers.add("x-name", "\t value with SP, HTAB\tand obs-text \u00ff\u0080 ");
        headers.add("x-name", newAsciiString("valid"));
        headers.add("x-name", "");
        assertThat(headers.size(), is(3));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("mutators")
    void invalidHeaderNameMessage(String mutatorName, HeaderMutator mutator) {
        assertInvalidNameMessage("0x3d ('=') at index=5 of length=9 in header name 'x-foo=bar'",
                () -> mutator.apply(newHeaders(), "x-foo=bar", "value"));
        assertInvalidNameMessage("0x3d ('=') at index=5 of length=9 in header name 'x-foo=bar'",
                () -> mutator.apply(newHeaders(), newAsciiString("x-foo=bar"), "value"));
    }

    @Test
    void invalidHeaderNameMessageVariants() {
        final HttpHeaders headers = newHeaders();
        assertInvalidNameMessage("0x3d ('=') at index=0 of length=1 in header name '='", () -> headers.add("=", "foo"));
        assertInvalidNameMessage("0x3d ('=') at index=5 of length=9 in header name 'x-foo=bar'",
                () -> headers.add(newAsciiString("x-foo=bar"), "foo"));
        assertInvalidNameMessage("0x20 (SP) at index=1 of length=5 in header name 'x foo'",
                () -> headers.set("x foo", "foo"));
        assertInvalidNameMessage("0x0a (LF) at index=5 of length=9 in header name 'x-foo\\x0abar'",
                () -> headers.add("x-foo\nbar", "foo"));
        assertInvalidNameMessage("0x0d (CR) at index=5 of length=7 in header name 'x-foo\\x0d\\x0a'",
                () -> headers.add(newAsciiString("x-foo\r\n"), "foo"));
        assertInvalidNameMessage("0x3d ('=') at index=3 of length=4 in header name 'x-\\u0141='",
                () -> headers.add("x-\u0141=", "foo"));
        assertInvalidNameMessage("0xc4 at index=2 of length=3 in header name 'x-\\xc4'",
                () -> headers.add("x-\u00c4", "foo"));
    }

    @Test
    void invalidHeaderNameMessageWhenCopiedFromNonValidatingHeaders() {
        DefaultHttpHeaders source = newHeaders(false, false);
        source.add("x foo", "value");
        assertInvalidNameMessage("0x20 (SP) at index=1 of length=5 in header name 'x foo'",
                () -> newHeaders(true, false).add(source));
    }

    private static Stream<Arguments> mutators() {
        return Stream.of(
                mutator("add(name, value)", HttpHeaders::add),
                mutator("add(name, Iterable)", (h, n, v) -> h.add(n, asList("valid", v))),
                mutator("add(name, varargs)", (h, n, v) -> h.add(n, "valid", v)),
                mutator("set(name, value)", HttpHeaders::set),
                mutator("set(name, Iterable)", (h, n, v) -> h.set(n, asList("valid", v))),
                mutator("set(name, varargs)", (h, n, v) -> h.set(n, "valid", v)));
    }

    private static Arguments mutator(String name, HeaderMutator mutator) {
        return arguments(name, mutator);
    }

    @FunctionalInterface
    private interface HeaderMutator {
        void apply(HttpHeaders headers, CharSequence name, CharSequence value);
    }

    private static void assertInvalidValueMessage(Executable executable) {
        final IllegalCharacterException e = assertThrows(IllegalCharacterException.class, executable);
        assertThat(e.getMessage(), is(INVALID_VALUE_MESSAGE));
        assertNoSecretAndPrintable(e.getMessage());
    }

    private static void assertInvalidNameMessage(String expectedPrefix, Executable executable) {
        final IllegalCharacterException e = assertThrows(IllegalCharacterException.class, executable);
        assertThat(e.getMessage(), is(expectedPrefix + TOKEN_EXPECTED));
    }

    private static void assertNoSecretAndPrintable(String message) {
        assertThat("Header value must not be included in the message", message, not(containsString(SECRET)));
        for (int i = 0; i < message.length(); ++i) {
            final char c = message.charAt(i);
            assertThat("Non-printable character 0x" + Integer.toHexString(c) + " in message: " + message,
                    c >= 0x20 && c < 0x7f, is(true));
        }
    }

    private static DefaultHttpHeaders newHeaders(boolean validateNames, boolean validateValues) {
        return new DefaultHttpHeaders(16, validateNames, false, validateValues);
    }

    @Override
    protected HttpHeaders newHeaders() {
        return DefaultHttpHeadersFactory.INSTANCE.newHeaders();
    }

    @Override
    protected HttpHeaders newHeaders(final int initialSizeHint) {
        return new DefaultHttpHeaders(initialSizeHint, true, true, true);
    }
}
