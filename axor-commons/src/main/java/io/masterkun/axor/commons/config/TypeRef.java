package io.masterkun.axor.commons.config;

public record TypeRef(boolean nullable, Class<?> parentType, Class<?>... paramTypes) {

}
