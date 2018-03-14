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
package io.servicetalk.concurrent.api;

import java.util.Optional;

import javax.annotation.Nullable;

import static java.util.Optional.ofNullable;

public final class Notification<T> {

    private static final Notification<Void> COMPLETE = new Notification<>(Type.OnComplete, null);
    private static final Notification<Void> CANCEL = new Notification<>(Type.OnCancel, null);

    private final Type type;
    @Nullable private final T data;

    private Notification(Type type, @Nullable T data) {
        this.type = type;
        this.data = data;
    }

    public enum Type {
        OnNext,
        OnComplete,
        OnError,
        OnRequest,
        OnCancel
    }

    /**
     * Returns {@link Type} of this {@link Notification}.
     *
     * @return {@link Type} of this {@link Notification}.
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns data, if any associated with this {@link Notification}.
     *
     * @return data, for this {@link Notification}.
     */
    @Nullable
    public Optional<T> getData() {
        return ofNullable(data);
    }

    public static <T> Notification<T> onNext(T item) {
        return new Notification<>(Type.OnNext, item);
    }

    public static Notification<Throwable> onError(Throwable cause) {
        return new Notification<>(Type.OnError, cause);
    }

    public static Notification<Void> onComplete() {
        return COMPLETE;
    }

    public static Notification<Long> onRequest(long n) {
        return new Notification<>(Type.OnRequest, n);
    }

    public static Notification<Void> onCancel() {
        return CANCEL;
    }
}
