package de.db.i4i.kura.wire.audio.collector;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Map;

import org.eclipse.kura.configuration.ConfigurationService;

public class AudioCollectorOptions {

	private static final String MIXER_NAME = "mixer.name";
	private static final String VOLUME_LEVEL = "volume.level";
	private static final String SAMPLE_RATE = "sample.rate";
	private static final String SAMPLE_SIZE = "sample.size";
	private static final String CHANNELS = "channels";
	private static final String SIGNED = "signed";
	private static final String BIG_ENDIAN = "big.endian";
	private static final String BUFFER_SIZE = "buffer.size";
	private static final String KURA_SERVICE_PID = ConfigurationService.KURA_SERVICE_PID;
	
	private final Map<String, Object> properties;
	
	AudioCollectorOptions(final Map<String, Object> properties) {
		requireNonNull(properties, "Properties must not be null");
		this.properties = properties;
	}
	
	String getMixerName() {
		String mixerName = null;
		final Object mn = this.properties.get(MIXER_NAME);
		if (nonNull(mn) && (mn instanceof String)) {
			mixerName = (String) mn;
		}
		return mixerName;
	}
	
	Float getVolumeLevel() {
		Float volumeLevel = null;
		final Object vl = this.properties.get(VOLUME_LEVEL);
		if (nonNull(vl) && (vl instanceof Float)) {
            volumeLevel = (Float) vl;
        }
        return volumeLevel;
	}

	Float getSampleRate() {
		Float sampleRate = null;
		final Object sr = this.properties.get(SAMPLE_RATE);
		if (nonNull(sr) && (sr instanceof Float)) {
            sampleRate = (Float) sr;
        }
        return sampleRate;
	}
	
	Integer getSampleSize() {
		Integer sampleSize = null;
		final Object ss = this.properties.get(SAMPLE_SIZE);
		if (nonNull(ss) && (ss instanceof Integer)) {
            sampleSize = (Integer) ss;
        }
        return sampleSize;
	}
	
	Integer getChannels() {
		Integer channels = null;
		final Object c = this.properties.get(CHANNELS);
		if (nonNull(c) && (c instanceof Integer)) {
            channels = (Integer) c;
        }
        return channels;
	}
	
	Boolean isSigned() {
		Boolean signed = null;
		final Object s = this.properties.get(SIGNED);
		if (nonNull(s) && (s instanceof Boolean)) {
            signed = (Boolean) s;
        }
        return signed;
	}
	
	Boolean isBigEndian() {
		Boolean bigEndian = null;
		final Object be = this.properties.get(BIG_ENDIAN);
		if (nonNull(be) && (be instanceof Boolean)) {
            bigEndian = (Boolean) be;
        }
        return bigEndian;
	}
	
	Integer getBufferSize() {
		Integer bufferSize = null;
		final Object bs = this.properties.get(BUFFER_SIZE);
		if (nonNull(bs) && (bs instanceof Integer)) {
            bufferSize = (Integer) bs;
        }
        return bufferSize;
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
