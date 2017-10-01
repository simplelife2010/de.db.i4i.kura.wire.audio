package de.db.i4i.kura.wire.audio.featureextractor;

import static java.util.Objects.requireNonNull;

import java.util.Map;

public class AudioMagSpectrumFeatureExtractorOptions {
	
	private final Map<String, Object> properties;
	
	AudioMagSpectrumFeatureExtractorOptions(final Map<String, Object> properties) {
		requireNonNull(properties, "Properties must not be null");
		this.properties = properties;
	}
}
