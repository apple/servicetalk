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
 * All header field names are compared in a
 * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">case-insensitive manner</a>.
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
     * @return a {@link Iterable} of header values or an empty {@link Iterable} if no values are found.
     *
     * @see #valuesIterator(CharSequence) if minimal object allocation is required.
     */
    @SuppressWarnings("unchecked")
    default Iterable<? extends CharSequence> values(CharSequence name) {
        return () -> (Iterator<CharSequence>) valuesIterator(name);
    }

    /**
     * Returns all values for the header with the specified name.
     *
     * @param name the name of the header to retrieve.
     * @return a {@link Iterator} of header values or an empty {@link Iterator} if no values are found.
     *
     * @see #values(CharSequence) if Iterable is preferred, at the expense of more object allocation.
     */
    Iterator<? extends CharSequence> valuesIterator(CharSequence name);

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
     *
     * @param name the header name.
     * @param value the header value of the header to find.
     * @return {@code true} if a {@code name}, {@code value} pair exists.
     */
    boolean contains(CharSequence name, CharSequence value);

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * <p>
     * Case insensitive compare is done on the value.
     *
     * @param name the name of the header to find.
     * @param value the value of the header to find.
     * @return {@code true} if found.
     */
    boolean containsIgnoreCase(CharSequence name, CharSequence value);

    /**
     * Returns the number of headers in this object.
     *
     * @return the number of headers in this object.
     */
    int size();

    /**
     * Returns {@code true} if {@link #size()} equals {@code 0}.
     *
     * @return {@code true} if {@link #size()} equals {@code 0}.
     */
    boolean isEmpty();

    /**
     * Returns a {@link Set} of all header names in this object. The returned {@link Set} cannot be modified.
     *
     * @return a {@link Set} of all header names in this object. The returned {@link Set} cannot be modified.
     */
    Set<? extends CharSequence> names();

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
     * @param name the name of the header.
     * @param values the values of the header.
     * @return {@code this}.
     */
    HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values);

    /**
     * Adds new headers with the specified {@code name} and {@code values}. This method is semantically equivalent to:
     *
     * <pre>
     * while (valueItr.hasNext()) {
     *     headers.add(name, valueItr.next());
     * }
     * </pre>
     *
     * @param name the name of the header.
     * @param valuesItr the values of the header.
     * @return {@code this}.
     */
    default HttpHeaders add(CharSequence name, Iterator<? extends CharSequence> valuesItr) {
        while (valuesItr.hasNext()) {
            add(name, valuesItr.next());
        }
        return this;
    }

    /**
     * Adds new headers with the specified {@code name} and {@code values}. This method is semantically equivalent to:
     *
     * <pre>
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the name of the header.
     * @param values the values of the header.
     * @return {@code this}.
     */
    HttpHeaders add(CharSequence name, CharSequence... values);

    /**
     * Adds all header names and values of {@code headers} to this object.
     *
     * @param headers the headers to add.
     * @return {@code this}.
     */
    HttpHeaders add(HttpHeaders headers);

    /**
     * Sets a header with the specified {@code name} and {@code value}. Any existing headers with the same name are
     * overwritten.
     *
     * @param name the name of the header.
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
     * @param name the name of the header.
     * @param values the values of the header.
     * @return {@code this}.
     */
    HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values);

    /**
     * Sets a new header with the specified {@code name} and {@code values}. This method is equivalent to:
     *
     * <pre>
     * headers.remove(name);
     * while (valueItr.hasNext()) {
     *     headers.add(name, valueItr.next());
     * }
     * </pre>
     *
     * @param name the name of the header.
     * @param valueItr the values of the header.
     * @return {@code this}.
     */
    default HttpHeaders set(CharSequence name, Iterator<? extends CharSequence> valueItr) {
        remove(name);
        while (valueItr.hasNext()) {
            add(name, valueItr.next());
        }
        return this;
    }

    /**
     * Sets a header with the specified {@code name} and {@code values}. Any existing headers with this name are
     * removed. This method is equivalent to:
     *
     * <pre>
     * headers.remove(name);
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the name of the header.
     * @param values the values of the header.
     * @return {@code this}.
     */
    HttpHeaders set(CharSequence name, CharSequence... values);

    /**
     * Clears the current header entries and copies all header entries of the specified {@code headers} object.
     *
     * @param headers the headers object which contains new values.
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
     * Removes all header names contained in {@code headers} from this object, and then adds all headers contained in
     * {@code headers}.
     *
     * @param headers the headers used to remove names and then add new entries.
     * @return {@code this}.
     */
    default HttpHeaders replace(final HttpHeaders headers) {
        if (headers != this) {
            for (final CharSequence key : headers.names()) {
                remove(key);
            }
            add(headers);
        }
        return this;
    }

    /**
     * Removes all headers with the specified {@code name}.
     *
     * @param name the name of the header.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean remove(CharSequence name);

    /**
     * Removes specific value(s) from the specified header {@code name}. If the header has more than one identical
     * values, all of them will be removed.
     *
     * @param name the name of the header.
     * @param value the value of the header to remove.
     * @return {@code true} if at least one value has been removed.
     */
    boolean remove(CharSequence name, CharSequence value);

    /**
     * Removes specific value(s) from the specified header {@code name}. If the header has more than one identical
     * values, all of them will be removed.
     * <p>
     * Case insensitive compare is done on the value.
     *
     * @param name the name of the header.
     * @param value the value of the header to remove.
     * @return {@code true} if at least one value has been removed.
     */
    boolean removeIgnoreCase(CharSequence name, CharSequence value);

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
     * Returns a {@link String} representation of this {@link HttpHeaders}. To avoid accidentally logging sensitive
     * information, implementations should be cautious about logging header content and consider using
     * {@link #toString(BiFunction)}.
     *
     * @return a simple {@link String} representation of this {@link HttpHeaders}.
     */
    @Override
    String toString();

    /**
     * Builds a string which represents all the content in this {@link HttpHeaders} in which sensitive headers can be
     * filtered via {@code filter}.
     *
     * @param filter a function that accepts the header name and value and returns the filtered value.
     * @return string representation of this {@link HttpHeaders}.
     */
    default String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return HeaderUtils.toString(this, filter);
    }

    /**
     * Gets a <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> identified by {@code name}. If there
     * is more than one {@link HttpSetCookie} for the specified name, the first {@link HttpSetCookie} in insertion order
     * is returned.
     *
     * @param name the cookie-name to look for.
     * @return a {@link HttpSetCookie} identified by {@code name}.
     */
    @Nullable
    HttpCookiePair getCookie(CharSequence name);

    /**
     * Gets a <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> identified by {@code name}. If
     * there is more than one {@link HttpSetCookie} for the specified name, the first {@link HttpSetCookie} in insertion
     * order is returned.
     *
     * @param name the cookie-name to look for.
     * @return a {@link HttpSetCookie} identified by {@code name}.
     */
    @Nullable
    HttpSetCookie getSetCookie(CharSequence name);

    /**
     * Gets all the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>s.
     *
     * @return An {@link Iterable} with all the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>s.
     *
     * @see #getCookiesIterator() if minimal object allocation is required.
     */
    @SuppressWarnings("unchecked")
    default Iterable<? extends HttpCookiePair> getCookies() {
        return () -> (Iterator<HttpCookiePair>) getCookiesIterator();
    }

    /**
     * Gets all the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>s.
     *
     * @return An {@link Iterator} with all the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>s.
     *
     * @see #getCookies() if Iterable is preferred, at the expense of more object allocation.
     */
    Iterator<? extends HttpCookiePair> getCookiesIterator();

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>s with the same name.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to get.
     * @return An {@link Iterable} where all the {@link HttpSetCookie}s have the same name.
     *
     * @see #getCookiesIterator(CharSequence) if minimal object allocation is required.
     */
    @SuppressWarnings("unchecked")
    default Iterable<? extends HttpCookiePair> getCookies(CharSequence name) {
        return () -> (Iterator<HttpCookiePair>) getCookiesIterator(name);
    }

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>s with the same name.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to get.
     * @return An {@link Iterator} where all the {@link HttpSetCookie}s have the same name.
     *
     * @see #getCookies(CharSequence) if Iterable is preferred, at the expense of more object allocation.
     */
    Iterator<? extends HttpCookiePair> getCookiesIterator(CharSequence name);

    /**
     * Gets all the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s.
     *
     * @return An {@link Iterable} with all the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s.
     *
     * @see #getSetCookiesIterator() if minimal object allocation is required.
     */
    @SuppressWarnings("unchecked")
    default Iterable<? extends HttpSetCookie> getSetCookies() {
        return () -> (Iterator<HttpSetCookie>) getSetCookiesIterator();
    }

    /**
     * Gets all the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s.
     *
     * @return An {@link Iterator} with all the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s.
     *
     * @see #getSetCookies() if Iterable is preferred, at the expense of more object allocation.
     */
    Iterator<? extends HttpSetCookie> getSetCookiesIterator();

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s with the same name.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to get.
     * @return An {@link Iterable} where all the {@link HttpSetCookie}s have the same name.
     *
     * @see #getSetCookiesIterator(CharSequence) if minimal object allocation is required.
     */
    @SuppressWarnings("unchecked")
    default Iterable<? extends HttpSetCookie> getSetCookies(CharSequence name) {
        return () -> (Iterator<HttpSetCookie>) getSetCookiesIterator(name);
    }

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s with the same name.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to get.
     * @return An {@link Iterator} where all the {@link HttpSetCookie}s have the same name.
     *
     * @see #getSetCookies(CharSequence) if Iterable is preferred, at the expense of more object allocation.
     */
    Iterator<? extends HttpSetCookie> getSetCookiesIterator(CharSequence name);

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s with the same name.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to get.
     * @param domain the domain-value of the {@link HttpSetCookie}s to get. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a> algorithm.
     * @param path the path-av of the {@link HttpSetCookie}s to get. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a> algorithm.
     * @return An {@link Iterable} where all the {@link HttpSetCookie}s match the parameter values.
     *
     * @see #getSetCookiesIterator(CharSequence, CharSequence, CharSequence) if minimal object allocation is required.
     */
    @SuppressWarnings("unchecked")
    default Iterable<? extends HttpSetCookie> getSetCookies(CharSequence name, CharSequence domain,
                                                            CharSequence path) {
        return () -> (Iterator<HttpSetCookie>) getSetCookiesIterator(name, domain, path);
    }

    /**
     * Gets the <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>s with the same name.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to get.
     * @param domain the domain-value of the {@link HttpSetCookie}s to get. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a> algorithm.
     * @param path the path-av of the {@link HttpSetCookie}s to get. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a> algorithm.
     * @return An {@link Iterator} where all the {@link HttpSetCookie}s match the parameter values.

     * @see #getSetCookies(CharSequence, CharSequence, CharSequence) if Iterable is preferred, at the expense of more
     * object allocation.
     */
    Iterator<? extends HttpSetCookie> getSetCookiesIterator(CharSequence name, CharSequence domain, CharSequence path);

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a>.
     * <p>
     * This may result in multiple {@link HttpCookiePair}s with same name.
     *
     * @param cookie the cookie to add.
     * @return {@code this}.
     */
    HttpHeaders addCookie(HttpCookiePair cookie);

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> with the specified {@code name} and
     * {@code value}.
     * <p>
     * This may result in multiple {@link HttpSetCookie}s with same name. Added cookie will not be wrapped, not secure,
     * and not HTTP-only, with no path, domain, expire date and maximum age.
     *
     * @param name the name of the cookie.
     * @param value the value of the cookie.
     * @return {@code this}.
     */
    default HttpHeaders addCookie(final CharSequence name, final CharSequence value) {
        return addCookie(new DefaultHttpCookiePair(name, value));
    }

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a>.
     * <p>
     * This may result in multiple {@link HttpSetCookie}s with same name.
     *
     * @param cookie the cookie to add.
     * @return {@code this}.
     */
    HttpHeaders addSetCookie(HttpSetCookie cookie);

    /**
     * Adds a <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> with the specified {@code name}
     * and {@code value}.
     * <p>
     * This may result in multiple {@link HttpSetCookie}s with same name. Added cookie will not be wrapped, not secure,
     * and not HTTP-only, with no path, domain, expire date and maximum age.
     *
     * @param name the name of the cookie.
     * @param value the value of the cookie.
     * @return {@code this}.
     */
    default HttpHeaders addSetCookie(final CharSequence name, final CharSequence value) {
        return addSetCookie(new DefaultHttpSetCookie(name, value));
    }

    /**
     * Removes all <a href="https://tools.ietf.org/html/rfc6265#section-4.2">cookie</a> identified by {@code name}.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to remove.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean removeCookies(CharSequence name);

    /**
     * Removes all <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> identified by {@code name}.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to remove.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean removeSetCookies(CharSequence name);

    /**
     * Removes all <a href="https://tools.ietf.org/html/rfc6265#section-4.1">set-cookie</a> identified by {@code name}.
     *
     * @param name the cookie-name of the {@link HttpSetCookie}s to remove.
     * @param domain the domain-value of the {@link HttpSetCookie}s to remove. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.3">Domain Matching</a> algorithm.
     * @param path the path-av of the {@link HttpSetCookie}s to remove. This value may be matched according
     * to the <a href="https://tools.ietf.org/html/rfc6265#section-5.1.4">Path Matching</a> algorithm.
     * @return {@code true} if at least one entry has been removed.
     */
    boolean removeSetCookies(CharSequence name, CharSequence domain, CharSequence path);
}
