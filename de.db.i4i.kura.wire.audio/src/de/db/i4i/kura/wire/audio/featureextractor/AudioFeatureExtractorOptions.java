package de.db.i4i.kura.wire.audio.featureextractor;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Map;

public class AudioFeatureExtractorOptions {
	
	private static final String SCRIPT_PATH = "script.path";
	private static final String SCRIPT_FILENAME = "script.filename";
	
	private final Map<String, Object> properties;
	
	AudioFeatureExtractorOptions(final Map<String, Object> properties) {
		requireNonNull(properties, "Properties must not be null");
		this.properties = properties;
	}
	
	String getScriptPath() {
		String scriptPath = null;
		final Object sp = this.properties.get(SCRIPT_PATH);
		if (nonNull(sp) && (sp instanceof String)) {
			scriptPath = (String) sp;
		}
		return scriptPath;
	}
	
	String getScriptFilename() {
		String scriptFilename = null;
		final Object sf = this.properties.get(SCRIPT_FILENAME);
		if (nonNull(sf) && (sf instanceof String)) {
			scriptFilename = (String) sf;
		}
		return scriptFilename;
	}
}
