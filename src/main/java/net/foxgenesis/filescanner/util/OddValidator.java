package net.foxgenesis.filescanner.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OddValidator implements ConstraintValidator<Odd, Integer> {

	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if (value == null) {
			return true; // Null values should be handled by @NotNull
		}
		return (value & 1) == 1;
	}
}