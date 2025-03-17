package io.masterkun.kactor.commons.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT})
public @interface ConfigField {
    String value() default "";

    Class<?>[] typeArges() default {};

    boolean nullable() default false;

    Class<? extends ConfigParser> parser() default DefaultConfigParser.class;
}
