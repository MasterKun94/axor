package io.masterkun.axor.commons.config;

import java.util.Arrays;
import java.util.stream.Collectors;

public record TypeRef(boolean nullable, Class<?> parentType, Class<?>... paramTypes) {
    @Override
    public String toString() {
        String parentName = parentType.getSimpleName();
        if (paramTypes.length == 0) {
            return parentName;
        } else {
            return Arrays.stream(paramTypes)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ", parentName + "<", ">"));
        }
    }
}
