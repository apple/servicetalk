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
package io.servicetalk.transport.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A factory to create different {@link TransportObserver}s.
 */
public final class TransportObservers {

    private TransportObservers() {
        // No instances.
    }

    /**
     * Coverts passed {@link TransportObserver} to a safe version that catches and logs all exceptions, but does not
     * rethrow them.
     *
     * @param observer {@link TransportObserver} to convert
     * @return a safe version of the passed {@link TransportObserver} that catches and logs all exceptions, but does not
     * rethrow them.
     */
    public static TransportObserver asSafeObserver(final TransportObserver observer) {
        if (observer instanceof CatchAllTransportObserver) {
            return observer;
        }
        if (observer instanceof BiTransportObserver) {
            // BiTransportObserver is always safe
            return observer;
        }
        return new CatchAllTransportObserver(observer);
    }

    /**
     * Combines multiple {@link TransportObserver}s into a single {@link TransportObserver}.
     *
     * @param other {@link TransportObserver}s to combine
     * @return a {@link TransportObserver} that delegates all invocations to the provided {@link TransportObserver}s
     */
    public static TransportObserver combine(final TransportObserver... other) {
        switch (other.length) {
            case 0:
                throw new IllegalArgumentException("At least one TransportObserver is required to combine");
            case 1:
                return requireNonNull(other[0]);
            default:
                BiTransportObserver bi = new BiTransportObserver(other[0], other[1]);
                for (int i = 2; i < other.length; i++) {
                    bi = new BiTransportObserver(bi, other[i]);
                }
                return bi;
        }
    }

    /**
     * Unpacks a {@link TransportObserver} into the list of observers it aggregates.
     * <p>
     * If the provided {@code observer} was created using the {@link #combine(TransportObserver...) combine} method,
     * this method returns the individual observers that were combined. Otherwise, it returns a singleton list
     * containing the provided observer.
     * <p>
     * Only the observers produced by this class are unwrapped: the aggregating observer created by
     * {@link #combine(TransportObserver...) combine} and the safe wrapper created by
     * {@link #asSafeObserver(TransportObserver) asSafeObserver}. Any other {@link TransportObserver} &mdash; including
     * a third-party implementation that internally wraps or aggregates other observers &mdash; is treated as opaque:
     * wherever it occurs in the combined structure it appears as a single element of the returned list, and its
     * contents are never unwrapped.
     *
     * @param observer {@link TransportObserver} to unpack
     * @return a {@link List} of {@link TransportObserver}s
     * @see #combine(TransportObserver...)
     */
    public static List<TransportObserver> unpack(final TransportObserver observer) {
        if (observer instanceof BiTransportObserver || observer instanceof CatchAllTransportObserver) {
            final List<TransportObserver> result = new ArrayList<>();
            unpack(observer, result);
            return Collections.unmodifiableList(result);
        }
        return Collections.singletonList(observer);
    }

    /*
     * Correctness of unpack, by structural induction.
     *
     * The parameter is non-null by contract (this package is @ElementsAreNonnullByDefault), so the proof reasons only
     * about non-null inputs.
     *
     * Structural forms. Every TransportObserver is exactly one of three "terms":
     *   - LEAF   L          : any observer that is neither a BiTransportObserver nor a CatchAllTransportObserver
     *                         (i.e. an observer supplied by the caller);
     *   - CATCH  CatchAll(x): a CatchAllTransportObserver wrapping a term x (produced by asSafeObserver, and
     *                         internally by combine via BiTransportObserver's constructor);
     *   - BI     Bi(a, b)   : a BiTransportObserver over terms a = first(), b = second().
     *
     * Well-formedness lemma (relied on by the induction; guaranteed by this package, not merely assumed):
     *   1. The three forms are pairwise disjoint. BiTransportObserver and CatchAllTransportObserver are distinct
     *      package-private *final* classes, so no object is an instance of both and callers cannot subclass either to
     *      blur the forms. Hence the if / else if / else below partitions all inputs, and the order of the instanceof
     *      checks is immaterial to correctness.
     *   2. The term reachable via first()/second()/observer() is a finite, acyclic tree. Both classes have *final*
     *      fields assigned once, at construction, from already-constructed subterms; producing a cycle would require
     *      mutating a final field or overriding first()/second(), both impossible for a final class with final state.
     *      Therefore recursion on strict subterms is well founded and terminates.
     *   3. No internal node is null. CatchAll's constructor calls requireNonNull, and every BI child passes through
     *      asSafeObserver (which returns an existing BI/CATCH or wraps a non-null LEAF), so first()/second()/observer()
     *      never yield null and every recursive call receives a non-null term.
     *
     * Specification. Define the intended result leaves(o) as the caller-supplied leaf observers, left to right:
     *   leaves(L)           = [L]
     *   leaves(CatchAll(x)) = leaves(x)           // the safe wrapper is transparent
     *   leaves(Bi(a, b))    = leaves(a) ++ leaves(b)
     *
     * Claim. unpack(o, result) appends exactly leaves(o) to result, preserving order.
     *
     * Proof by structural induction on the term o (well founded by lemma 2).
     *   Base case  (o = LEAF): by lemma 1 neither instanceof test matches, so the else branch appends o. That is
     *                          [L] = leaves(o).
     *   Step (o = Bi(a, b)):   by the induction hypothesis unpack(first(), result) appends leaves(a) and then
     *                          unpack(second(), result) appends leaves(b); first precedes second, so result gains
     *                          leaves(a) ++ leaves(b) = leaves(o). Subterms are non-null by lemma 3.
     *   Step (o = CatchAll(x)):by the induction hypothesis unpack(observer(), result) appends leaves(x) = leaves(o).
     *   In every case unpack(o, result) appends leaves(o). QED.
     *
     * Corollary (public method). If o is a LEAF, unpack(o) returns singletonList(o) = leaves(o); otherwise it seeds an
     * empty list, invokes the helper (which appends leaves(o) by the claim), and returns it. Hence unpack(o) =
     * leaves(o) for every non-null o, mirroring HttpLifecycleObservers.unpack. In particular combine(o1, ..., on)
     * unpacks back to [o1, ..., on]: combine wraps each argument with asSafeObserver (adding CATCH nodes that leaves()
     * sees through) and nests them in a left-leaning BI tree whose in-order leaf traversal is o1, ..., on.
     */
    private static void unpack(final TransportObserver observer, final List<TransportObserver> result) {
        if (observer instanceof BiTransportObserver) {
            final BiTransportObserver bi = (BiTransportObserver) observer;
            unpack(bi.first(), result);
            unpack(bi.second(), result);
        } else if (observer instanceof CatchAllTransportObserver) {
            // combine(...) wraps each combined observer with asSafeObserver(...), so unwrap to expose the original
            // leaf observer that was provided by the user.
            unpack(((CatchAllTransportObserver) observer).observer(), result);
        } else {
            result.add(observer);
        }
    }
}
