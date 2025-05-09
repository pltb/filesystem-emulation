package io.github.pltb;

import java.io.IOException;

public interface CallableIOOperation<T> {
    T run() throws IOException;
}
