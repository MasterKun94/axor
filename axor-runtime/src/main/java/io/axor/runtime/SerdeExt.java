package io.axor.runtime;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface SerdeExt<T> extends Serde<T> {
    void doSerialize(T obj, DataOutput out) throws IOException;

    T doDeserialize(DataInput in) throws IOException;
}
