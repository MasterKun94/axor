package io.axor.runtime.internal;

import io.axor.runtime.TypeReference;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

public class TypeReferenceTest {

    @Test
    public void testTypeReference() {
        TypeReference<Map<String, String>> typeReference = new TypeReference<>() {
        };
        Type type = typeReference.getType();
        System.out.println(type);
        System.out.println(type.getClass());
        System.out.println(((ParameterizedType) type).getRawType());
        System.out.println(((ParameterizedType) type).getRawType().getClass());
        System.out.println(Arrays.toString(((ParameterizedType) type).getActualTypeArguments()));
        System.out.println(((ParameterizedType) type).getOwnerType());
    }

}
