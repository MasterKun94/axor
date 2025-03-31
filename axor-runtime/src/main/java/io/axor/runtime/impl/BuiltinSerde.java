package io.axor.runtime.impl;

import io.axor.runtime.SerdeByteArrayInputStreamAdaptor;
import io.axor.runtime.SerdeExt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

public interface BuiltinSerde<T> extends SerdeExt<T> {
    @Override
    default InputStream serialize(T object) {
        return new SerdeByteArrayInputStreamAdaptor<>((stream, data) -> {
            var dout = stream instanceof DataOutputStream d ? d : new DataOutputStream(stream);
            int start = dout.size();
            doSerialize(data, dout);
            return dout.size() - start;
        }, object);
    }

    @Override
    default T deserialize(InputStream stream) throws IOException {
        var din = stream instanceof DataInputStream d ? d : new DataInputStream(stream);
        return doDeserialize(din);
    }

    @Override
    default String getImpl() {
        return "builtin";
    }
}
