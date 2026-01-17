package com.izemtechnologies.logging.masking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields, parameters, or methods as containing sensitive data.
 * 
 * <p>When applied, the logging system will automatically mask the annotated value
 * according to the specified {@link MaskingMode}.</p>
 * 
 * <h2>Usage Examples:</h2>
 * <pre>{@code
 * // On a record field
 * public record UserDto(
 *     String id,
 *     @Sensitive(type = SensitiveType.EMAIL) String email,
 *     @Sensitive(mode = MaskingMode.FULL) String password,
 *     @Sensitive(type = SensitiveType.PHONE) String phoneNumber,
 *     @Sensitive(type = SensitiveType.CREDIT_CARD) String cardNumber
 * ) {}
 * 
 * // On a method parameter
 * public void processPayment(
 *     @Sensitive(type = SensitiveType.CREDIT_CARD) String cardNumber,
 *     @Sensitive(mode = MaskingMode.HASH) String cvv
 * ) { ... }
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 * @see MaskingMode
 * @see SensitiveType
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {

    /**
     * The type of sensitive data. Used to apply type-specific masking patterns.
     * 
     * @return the sensitive data type
     */
    SensitiveType type() default SensitiveType.GENERIC;

    /**
     * The masking mode to apply.
     * 
     * @return the masking mode
     */
    MaskingMode mode() default MaskingMode.PARTIAL;

    /**
     * Custom pattern for masking (regex). Only used when type is CUSTOM.
     * 
     * @return the custom regex pattern
     */
    String pattern() default "";

    /**
     * Custom replacement string. Use $1, $2, etc. for capture groups.
     * 
     * @return the replacement string
     */
    String replacement() default "";
}
