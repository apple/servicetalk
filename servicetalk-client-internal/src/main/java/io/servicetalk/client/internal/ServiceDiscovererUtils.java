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
package io.servicetalk.client.internal;

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.RandomAccess;
import javax.annotation.Nullable;

import static java.util.Collections.binarySearch;

/**
 * A set of utility functions for {@link ServiceDiscoverer}.
 */
public final class ServiceDiscovererUtils {

    private ServiceDiscovererUtils() {
        // no instances
    }

    /**
     * Give a sorted list of currently active addresses, and a new set of unsorted active address calculate the {@link ServiceDiscoverer.Event}s.
     * <p>
     * {@code newActiveAddresses} will be sorted in this method.
     * @param currentActiveAddresses The currently active addresses.
     * @param newActiveAddresses The new list of active addresses.
     * @param comparator A comparator for the addresses and to use for binary searches.
     * @param <T> The type of address.
     * @return A list of {@link ServiceDiscoverer.Event}s which represents the changes between {@code currentActiveAddresses} and {@code newActiveAddresses}, or {@code null} if there are no changes.
     */
    @Nullable
    public static <T> List<ServiceDiscoverer.Event<T>> calculateDifference(List<? extends T> currentActiveAddresses, List<? extends T> newActiveAddresses, Comparator<T> comparator) {
        // First sort the newAddresses so we can use binary search.
        newActiveAddresses.sort(comparator);

        // Calculate removals (in activeAddresses, not in newAddresses).
        return relativeComplement(false, newActiveAddresses, currentActiveAddresses, comparator,
                // Calculate additions (in newAddresses, not in activeAddresses).
                relativeComplement(true, currentActiveAddresses, newActiveAddresses, comparator, null));
    }

    /**
     * Calculate the relative complement of {@code sortedA} and {@code sortedB} (elements in {@code sortedB} and not in {@code sortedA}).
     * <p>
     * See <a href="https://en.wikipedia.org/wiki/Venn_diagram#Overview">Set Mathematics</a>.
     * @param isAvailable Will be used for {@link ServiceDiscoverer.Event#available()} for each {@link ServiceDiscoverer.Event} in the returned {@link List}.
     * @param sortedA A sorted {@link List} of which no elements be present in the return value.
     * @param sortedB A sorted {@link List} of which elements in this set that are not in {@code sortedA} will be in the return value.
     * @param comparator Used for binary searches on {@code sortedA} for each element in {@code sortedB}.
     * @param result List to append new results to.
     * @param <T> The type of resolved address.
     * @return the relative complement of {@code sortedA} and {@code sortedB} (elements in {@code sortedB} and not in {@code sortedA}).
     */
    @Nullable
    private static <T> List<ServiceDiscoverer.Event<T>> relativeComplement(boolean isAvailable,
                                                                           List<? extends T> sortedA,
                                                                           List<? extends T> sortedB,
                                                                           Comparator<T> comparator,
                                                                           @Nullable List<ServiceDiscoverer.Event<T>> result) {
        if (sortedB instanceof RandomAccess) {
            for (int i = 0; i < sortedB.size(); ++i) {
                final T valueB = sortedB.get(i);
                if (binarySearch(sortedA, valueB, comparator) < 0) {
                    if (result == null) {
                        result = new ArrayList<>(4);
                    }
                    result.add(new DefaultServiceDiscovererEvent<>(valueB, isAvailable));
                }
            }
        } else {
            for (T valueB : sortedB) {
                if (binarySearch(sortedA, valueB, comparator) < 0) {
                    if (result == null) {
                        result = new ArrayList<>(4);
                    }
                    result.add(new DefaultServiceDiscovererEvent<>(valueB, isAvailable));
                }
            }
        }
        return result;
    }
}
