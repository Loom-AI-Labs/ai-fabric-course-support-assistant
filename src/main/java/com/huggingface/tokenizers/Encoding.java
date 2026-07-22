package com.huggingface.tokenizers;

/**
 * Reflection-compatible projection of DJL tokenizer output for AI Fabric 0.3.3.
 */
public final class Encoding {

    private final ai.djl.huggingface.tokenizers.Encoding delegate;

    Encoding(ai.djl.huggingface.tokenizers.Encoding delegate) {
        this.delegate = delegate;
    }

    public long[] getIds() {
        return delegate.getIds();
    }

    public long[] getAttentionMask() {
        return delegate.getAttentionMask();
    }

    public long[] getTypeIds() {
        return delegate.getTypeIds();
    }
}
