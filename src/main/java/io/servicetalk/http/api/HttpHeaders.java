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
package io.servicetalk.http.api;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * HTTP <a href="https://tools.ietf.org/html/rfc7230.html#section-3.2">Header Fields</a>.
 * <p>
 * All header field names are compared in a <a href="https://tools.ietf.org/html/rfc7230#section-3.2">case-insensitive manner</a>.
 */
public interface HttpHeaders extends Iterable<Entry<CharSequence, CharSequence>> {

    /**
     * Returns the value of a header with the specified name. If there is more than one value for the specified name,
     * the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve.
     * @return the first header value if the header is found. {@code null} if there's no such header.
     */
    @Nullable
    CharSequence get(CharSequence name);

    /**
     * Returns the value of a header with the specified name. If there is more than one value for the specified name,
     * the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve.
     * @param defaultValue the default value.
     * @return the first header value or {@code defaultValue} if there is no such header.
     */
    default CharSequence get(final CharSequence name, final CharSequence defaultValue) {
        final CharSequence value = get(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the value of a header with the specified name and removes it from this object. If there is more than
     * one value for the specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve.
     * @return the first header value or {@code null} if there is no such header.
     */
    @Nullable
    CharSequence getAndRemove(CharSequence name);

    /**
     * Returns the value of a header with the specified name and removes it from this object. If there is more than
     * one value for the specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve.
     * @param defaultValue the default value.
     * @return the first header value or {@code defaultValue} if there is no such header.
     */
    default CharSequence getAndRemove(final CharSequence name, final CharSequence defaultValue) {
        final CharSequence value = getAndRemove(name);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns all values for the header with the specified name.
     *
     * @param name the name of the header to retrieve.
     * @return a {@link Iterator} of header values or an empty {@link Iterator} if no values are found.
     */
    Iterator<? extends CharSequence> getAll(CharSequence name);

    /**
     * Returns {@code true} if a header with the {@code name} exists, {@code false} otherwise.
     *
     * @param name the header name.
     * @return {@code true} if {@code name} exists.
     */
    default boolean contains(final CharSequence name) {
        return get(name) != null;
    }

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * @param name the header name.
     * @param value the header value of the header to find.
     * @return {@code true} if a {@code name}, {@code value} pair exists.
     */
    boolean contains(CharSequence name, CharSequence value);

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * This also handles multiple values that are separated with a {@code ,}.
     * <p>
     * If {@code caseInsensitive} is {@code true} then a case insensitive compare is done on the value.
     * @param name the name of the header to find.
     * @param value the value of the header to find.
     * @param caseInsensitive {@code true} then a case insensitive compare is run to compare values,
     * otherwise a case sensitive compare is run to compare values.
     * @return {@code true} if found.
     */
    boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive);

    /**
     * Returns the number of headers in this object.
     * @return the number of headers in this object.
     */
    int size();

    /**
     * Returns {@code true} if {@link #size()} equals {@code 0}.
     * @return {@code true} if {@link #size()} equals {@code 0}.
     */
    boolean isEmpty();

    /**
     * Returns a {@link Set} of all header names in this object. The returned {@link Set} cannot be modified.
     * @return a {@link Set} of all header names in this object. The returned {@link Set} cannot be modified.
     */
    Set<? extends CharSequence> getNames();

    /**
     * Adds a new header with the specified {@code name} and {@code value}.
     *
     * @param name the name of the header.
     * @param value the value of the header.
     * @return {@code this}.
     */
    HttpHeaders add(CharSequence name, CharSequence value);

    /**
     * Adds new headers with the specified {@code name} and {@code values}. This method is semantically equivalent to:
     *
     * <pre>
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the header name.
     * @param values the values of the header.
     * @return {@code this}.
     */
    HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values);

    /**
     * Adds new headers with the specified {@code name} and {@code values}. This method is semantically equivalent to:
     *
     * <pre>
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the header name.
     * @param values the values of the header.
     * @return {@code this}.
     */
    @SuppressWarnings("unchecked")
    HttpHeaders add(CharSequence name, CharSequence... values);

    /**
     * Adds all header names and values of {@code headers} to this object.
     *
     * @param headers The headers to add.
     * @return {@code this}
     * @throws IllegalArgumentException if {@code headers == this}.
     */
    HttpHeaders add(HttpHeaders headers);

    /**
     * Sets a header with the specified {@code name} and {@code value}. Any existing headers with the same name are overwritten.
     *
     * @param name the header name.
     * @param value the value of the header.
     * @return {@code this}.
     */
    HttpHeaders set(CharSequence name, CharSequence value);

    /**
     * Sets a new header with the specified {@code name} and {@code values}. This method is equivalent to:
     *
     * <pre>
     * headers.remove(name);
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the header name.
     * @param values the value of the header.
     * @return {@code this}.
     */
    HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values);

    /**
     * Sets a header with the specified {@code name} and {@code values}. Any existing headers with this name are removed. This method
     * is equivalent to:
     *
     * <pre>
     * headers.remove(name);
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the header name.
     * @param values the value of the header.
     * @return {@code this}.
     */
    @SuppressWarnings("unchecked")
    HttpHeaders set(CharSequence name, CharSequence... values);

    /**
     * Clears the current header entries and copies all header entries of the specified {@code headers}.
     *
     * @param headers The headers which contains the new values for this {@link HttpHeaders}.
     * @return {@code this}.
     */
    default HttpHeaders set(final HttpHeaders headers) {
        if (headers != this) {
            clear();
            add(headers);
        }
        return this;
    }

    /**
     * Removes all header names contained in {@code headers} from this object, and then adds all headers contained in {@code headers}.
     *
     * @param headers The headers used to remove names and then add new entries.
     * @return {@code this}.
     */
    default HttpHeaders setAll(final HttpHeaders headers) {
        if (headers != this) {
            for (final CharSequence key : headers.getNames()) {
                remove(key);
            }
            add(headers);
        }
        return this;
    }

    /**
     * Removes all headers with the specified {@code name}.
     *
     * @param name the header name.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean remove(CharSequence name);

    /**
     * Removes specific value(s) from the specified header {@code name}. If the header has more than one identical
     * values, all of them will be removed. A <i>case sensitive</i> compare is run to compare values. This is identical
     * to {@link #remove(CharSequence, CharSequence, boolean)} with the third argument equals to {@code false}.
     *
     * @param name the header name.
     * @param value the value of the header to remove.
     * @return {@code true} if at least one value has been removed.
     * @see #remove(CharSequence, CharSequence, boolean)
     */
    boolean remove(CharSequence name, CharSequence value);

    /**
     * Removes specific value(s) from the specified header {@code name}. If the header has more than one identical
     * values, all of them will be removed.
     * <p>
     * If {@code ignoreCase} is {@code true} then a case insensitive compare is done on the value.
     *
     * @param name the header name.
     * @param value the value of the header to remove.
     * @param caseInsensitive {@code true} then a case insensitive compare is run to compare values,
     * otherwise a case sensitive compare is run to compare values.
     * @return {@code true} if at least one value has been removed.
     */
    boolean remove(CharSequence name, CharSequence value, boolean caseInsensitive);

    /**
     * Removes all headers.
     *
     * @return {@code this}.
     */
    HttpHeaders clear();

    @Override
    Iterator<Entry<CharSequence, CharSequence>> iterator();

    @Override
    default Spliterator<Entry<CharSequence, CharSequence>> spliterator() {
        return Spliterators.spliterator(iterator(), size(), Spliterator.SIZED);
    }

    /**
     * Create a deep copy of this {@link HttpHeaders} object.
     * @return a deep copy of this {@link HttpHeaders} object. For special header types this may be the same instance as this.
     */
    HttpHeaders copy();

    /**
     * Returns a {@link String} representation of this {@link HttpHeaders}. To avoid accidentally logging sensitive information,
     * implementations should be cautious about logging header content and consider using {@link #toString(BiFunction)}.
     *
     * @return a simple {@link String} representation of this {@link HttpHeaders}.
     */
    @Override
    String toString();

    /**
     * Build a string which represents all the content in this {@link HttpHeaders} in which sensitive headers can be filtered
     * via {@code filter}.
     * @param filter a function that accepts the header name and value and returns the filtered value.
     * @return string representation of this {@link HttpHeaders}.
     */
    String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter);

    /**
     * Parse the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers and return it as a {@link HttpCookies}.
     * <p>
     * Updates made to the {@link HttpCookies} object will be reflected in this {@link HttpHeaders}
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers after {@link HttpCookies#encodeToHttpHeaders()}
     * is invoked. However the reverse direction is <strong>not</strong> true. If updates are made to the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers it will not be visible in the returned
     * {@link HttpCookies}. In this case you <strong>must</strong> call {@link #parseCookies()} again to get the changed state.
     * @return a {@link HttpCookies} representation of the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers.
     * @see HttpCookies#encodeToHttpHeaders()
     */
    default HttpCookies parseCookies() {
        return parseCookies(true);
    }

    /**
     * Parse the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers and return it as a {@link HttpCookies}.
     * <p>
     * Updates made to the {@link HttpCookies} object will be reflected in this {@link HttpHeaders}
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers after {@link HttpCookies#encodeToHttpHeaders()}
     * is invoked. However the reverse direction is <strong>not</strong> true. If updates are made to the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers it will not be visible in the returned
     * {@link HttpCookies}. In this case you <strong>must</strong> call {@link #parseCookies(boolean)} again to get the changed state.
     * @param validateContent {@code true} to validate the content of the cookies while parsing.
     * @return a {@link HttpCookies} representation of the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> headers.
     * @see HttpCookies#encodeToHttpHeaders()
     */
    HttpCookies parseCookies(boolean validateContent);

    /**
     * Parse the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers and return it as a {@link HttpCookies}.
     * <p>
     * Updates made to the {@link HttpCookies} object will be reflected in this {@link HttpHeaders}
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers after {@link HttpCookies#encodeToHttpHeaders()}
     * is invoked. However the reverse direction is <strong>not</strong> true. If updates are made to the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers it will not be visible in the returned
     * {@link HttpCookies}. In this case you <strong>must</strong> call {@link #parseSetCookies()} again to get the changed state.
     * @return a {@link HttpCookies} representation of the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers.
     * @see HttpCookies#encodeToHttpHeaders()
     */
    default HttpCookies parseSetCookies() {
        return parseSetCookies(true);
    }

    /**
     * Parse the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers and return it as a {@link HttpCookies}.
     * <p>
     * Updates made to the {@link HttpCookies} object will be reflected in this {@link HttpHeaders}
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers after {@link HttpCookies#encodeToHttpHeaders()}
     * is invoked. However the reverse direction is <strong>not</strong> true. If updates are made to the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers it will not be visible in the returned
     * {@link HttpCookies}. In this case you <strong>must</strong> call {@link #parseSetCookies(boolean)} again to get the changed state.
     * @param validateContent {@code true} to validate the content of the cookies while parsing.
     * @return a {@link HttpCookies} representation of the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> headers.
     * @see HttpCookies#encodeToHttpHeaders()
     */
    HttpCookies parseSetCookies(boolean validateContent);
}
