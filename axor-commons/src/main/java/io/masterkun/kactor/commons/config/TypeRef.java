package io.masterkun.kactor.commons.config;

public record TypeRef(boolean nullable, Class<?> parentType, Class<?>... paramTypes) {

}
