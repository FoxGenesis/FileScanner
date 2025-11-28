package net.foxgenesis.filescanner.cascade;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import net.foxgenesis.filescanner.util.Odd;

@Getter
@Setter
@Validated
public class OpenCVProperties {

	@Valid
	private PreProcessSettings pre = new PreProcessSettings();

	// =====================================================

	@Getter
	@Setter
	@Validated
	public class PreProcessSettings {
		@Min(0)
		@Max(360)
		private double rotation = 90;

		@Odd
		@Min(1)
		private int blurSize = 5;
	}
}
