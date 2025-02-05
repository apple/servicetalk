package io.servicetalk.concurrent.api;

import static java.util.Objects.requireNonNull;

final class CustomCaptureAsyncContextProvider extends AbstractAsyncContextProvider {

    private final ContextCaptureProvider delegate;

    CustomCaptureAsyncContextProvider(ContextCaptureProvider delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public CapturedContext captureContext() {
        return delegate.captureContext();
    }

    @Override
    public CapturedContext captureContextCopy() {
        return delegate.captureContextCopy();
    }
}
