package de.db.i4i.kura.wire.audio.filewriter;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Map;

public class AudioFileWriterOptions {

	private static final String OUTPUT_FILE_PATH = "output.file.path";
	
	private final Map<String, Object> properties;
	
	AudioFileWriterOptions(final Map<String, Object> properties) {
		requireNonNull(properties, "Properties cannot be null");
		this.properties = properties;
	}
	
	String getOutputFilePath() {
		String outputFilePath = null;
		final Object ofp = this.properties.get(OUTPUT_FILE_PATH);
		if (nonNull(ofp) && (ofp instanceof String)) {
            outputFilePath = ofp.toString();
        }
        return outputFilePath;
	}
}
