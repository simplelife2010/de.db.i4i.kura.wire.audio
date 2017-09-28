package de.db.i4i.kura.wire.audio.concatenator;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Map;

public class AudioConcatenatorOptions {
	
	private static final String CONCATENATION_DURATION = "concatenation.duration";
	
	private final Map<String, Object> properties;
	
	AudioConcatenatorOptions(final Map<String, Object> properties) {
		requireNonNull(properties, "Properties must not be null");
		this.properties = properties;
	}
	
	Integer getConcatenationDuration() {
		Integer concatenationDuration = null;
		final Object cd = this.properties.get(CONCATENATION_DURATION);
		if (nonNull(cd) && (cd instanceof Integer)) {
            concatenationDuration = (Integer) cd;
        }
        return concatenationDuration;
	}

}
