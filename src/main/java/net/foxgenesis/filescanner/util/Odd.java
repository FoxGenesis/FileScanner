package net.foxgenesis.filescanner.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = OddValidator.class)
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
public @interface Odd {
	String message() default "must be an odd number";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}