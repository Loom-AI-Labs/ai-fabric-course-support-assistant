package com.huggingface.tokenizers;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Adapts AI Fabric 0.3.3's optional tokenizer hook to DJL's Hugging Face tokenizer.
 */
public final class Tokenizer implements AutoCloseable {

    private final HuggingFaceTokenizer delegate;

    private Tokenizer(HuggingFaceTokenizer delegate) {
        this.delegate = delegate;
    }

    public static Tokenizer fromFile(String path) throws IOException {
        return new Tokenizer(HuggingFaceTokenizer.newInstance(Path.of(path)));
    }

    public Encoding encode(String text) {
        return new Encoding(delegate.encode(text));
    }

    public Encoding encode(String text, boolean addSpecialTokens) {
        return new Encoding(delegate.encode(text, addSpecialTokens, false));
    }

    @Override
    public void close() {
        delegate.close();
    }
}
