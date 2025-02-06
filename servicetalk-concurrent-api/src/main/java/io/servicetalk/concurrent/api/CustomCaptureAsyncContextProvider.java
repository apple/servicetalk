package io.servicetalk.concurrent.api;

import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

final class CustomCaptureAsyncContextProvider extends DefaultAsyncContextProvider {

    private final CapturedContextProvider delegate;

    CustomCaptureAsyncContextProvider(CapturedContextProvider delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public CapturedContext captureContext(ContextMap contextMap) {
        return delegate.captureContext(super.captureContext(contextMap));
    }
}
