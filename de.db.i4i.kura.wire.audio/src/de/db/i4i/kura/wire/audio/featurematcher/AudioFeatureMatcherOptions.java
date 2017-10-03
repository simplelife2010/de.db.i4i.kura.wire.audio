package de.db.i4i.kura.wire.audio.featurematcher;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Map;

public class AudioFeatureMatcherOptions {
	
	private static final String CODEBOOK_PATH = "codebook.path";
	private static final String CODEBOOK_FILENAME = "codebook.filename";
	
	private final Map<String, Object> properties;
	
	AudioFeatureMatcherOptions(final Map<String, Object> properties) {
		requireNonNull(properties, "Properties must not be null");
		this.properties = properties;
	}
	
	String getCodebookPath() {
		String codebookPath = null;
		final Object sp = this.properties.get(CODEBOOK_PATH);
		if (nonNull(sp) && (sp instanceof String)) {
			codebookPath = (String) sp;
		}
		return codebookPath;
	}
	
	String getCodebookFilename() {
		String codebookFilename = null;
		final Object sf = this.properties.get(CODEBOOK_FILENAME);
		if (nonNull(sf) && (sf instanceof String)) {
			codebookFilename = (String) sf;
		}
		return codebookFilename;
	}
}
