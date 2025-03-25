package io.masterkun.axor.commons.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a record component as a configuration field, providing metadata and
 * customization options for how the field should be parsed from a configuration.
 *
 * <p>This annotation is typically used in conjunction with a configuration mapping framework that
 * processes records and maps their components to values from a configuration source.
 *
 * @see ConfigParser
 * @see DefaultConfigParser
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT})
public @interface ConfigField {
    /**
     * Specifies the key name to be used for this configuration field when parsing from a
     * configuration source. If not specified, the record component's name will be used as the key.
     *
     * @return the key name for the configuration field, or an empty string if the default should be
     * used
     */
    String value() default "";

    /**
     * Specifies the type arguments for a generic type. This is useful when the configuration field
     * is a parameterized type, such as a list or a map, and you need to specify the types of the
     * parameters.
     *
     * @return an array of Class objects representing the type arguments, or an empty array if no
     * type arguments are specified
     */
    Class<?>[] typeArges() default {};

    /**
     * Indicates whether the configuration field can accept a null value.
     *
     * @return true if the field is nullable, false otherwise
     */
    boolean nullable() default false;

    /**
     * Specifies the parser to be used for parsing the configuration field.
     *
     * @return the class of the {@link ConfigParser} to be used, or {@link DefaultConfigParser} if
     * not specified
     */
    Class<? extends ConfigParser> parser() default DefaultConfigParser.class;

    /**
     * Specifies a fallback value to be used if the configuration field is not found or cannot be
     * parsed.
     *
     * @return the fallback value as a String, or an empty string if no fallback is provided
     */
    String fallback() default "";
}
