package io.axor.commons.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a record component as a configuration origin, indicating that the
 * component should be populated with the entire configuration or the remaining configuration after
 * other fields have been parsed.
 *
 * <p>This annotation is typically used in conjunction with a configuration mapping framework that
 * processes records and maps their components to values from a configuration source. When this
 * annotation is present, the annotated field will receive either the entire configuration or the
 * remaining configuration, depending on the value of the {@code retainAll} attribute.
 *
 * @see ConfigMapper
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT})
public @interface ConfigOrigin {
    boolean retainAll() default false;
}
