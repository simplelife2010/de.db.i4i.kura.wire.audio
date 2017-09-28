package de.db.i4i.kura.wire.audio.filereader;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Map;

import org.eclipse.kura.configuration.ConfigurationService;

public class AudioFileReaderOptions {

	private static final String PATH = "path";
	private static final String FILENAME = "filename";
	private static final String KURA_SERVICE_PID = ConfigurationService.KURA_SERVICE_PID;
	
	private final Map<String, Object> properties;
	
	AudioFileReaderOptions(final Map<String, Object> properties) {
		requireNonNull(properties, "Properties must not be null");
		this.properties = properties;
	}
	
	String getPath() {
		String path = null;
		final Object p = this.properties.get(PATH);
		if (nonNull(p) && (p instanceof String)) {
			path = (String) p;
		}
		return path;
	}
	
	String getFilename() {
		String filename = null;
		final Object f = this.properties.get(FILENAME);
		if (nonNull(f) && (f instanceof String)) {
			filename = (String) f;
		}
		return filename;
	}
	
	String getKuraServicePid() {
		String kuraServicePid = null;
		final Object ksp = this.properties.get(KURA_SERVICE_PID);
		if (nonNull(ksp) && (ksp instanceof String)) {
			kuraServicePid = (String) ksp;
		}
		return kuraServicePid;
	}
}
