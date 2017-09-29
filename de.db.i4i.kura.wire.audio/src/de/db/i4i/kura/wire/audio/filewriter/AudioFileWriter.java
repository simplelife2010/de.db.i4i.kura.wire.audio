package de.db.i4i.kura.wire.audio.filewriter;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.type.DataType;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireReceiver;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.service.wireadmin.Wire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.db.i4i.kura.wire.audio.AudioWireRecordProperties;

public class AudioFileWriter implements WireEmitter, WireReceiver, ConfigurableComponent {

	private static final Logger logger = LoggerFactory.getLogger(AudioFileWriter.class);
	
	private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioFileWriterOptions options;
	
	public void bindWireHelperService(final WireHelperService wireHelperService) {
        if (isNull(this.wireHelperService)) {
            this.wireHelperService = wireHelperService;
        }
    }

    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }
	
	protected void activate(final Map<String, Object> properties) {
		logger.debug("Activating AudioFileWriter...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        logger.debug("Activating AudioFileWriter... Done");
	}
	
    protected void deactivate() {
		logger.debug("Deactivating AudioFileWriter...");
        logger.debug("Deactivating AudioFileWriter... Done");
	}
	
	public void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioFileWriter...");
        this.extractProperties(properties);
        logger.debug("Updating AudioFileWriter... Done");
	}
    
	@Override
    public void updated(Wire wire, Object value) {
        this.wireSupport.updated(wire, value);
    }

    @Override
    public void producersConnected(Wire[] wires) {
        this.wireSupport.producersConnected(wires);
    }

    @Override
	public void onWireReceive(WireEnvelope wireEnvelope) {
    	requireNonNull(wireEnvelope, "Wire envelope must not be null");
		logger.debug("Received wire envelope with {} record(s) from {}", wireEnvelope.getRecords().size(), wireEnvelope.getEmitterPid());
		
		final List<WireRecord> audioFileWriterWireRecords = new ArrayList<>();
		for (WireRecord record : wireEnvelope.getRecords()) {
      
            String source = (String) getPropertyValue(record, DataType.STRING, AudioWireRecordProperties.SOURCE);
            long timestamp = (long) getPropertyValue(record, DataType.LONG, AudioWireRecordProperties.TIMESTAMP);
            byte[] audioData = (byte[]) getPropertyValue(record, DataType.BYTE_ARRAY, AudioWireRecordProperties.AUDIO_DATA);
            Float sampleRate = (Float) getPropertyValue(record, DataType.FLOAT, AudioWireRecordProperties.SAMPLE_RATE);
            Integer sampleSize = (Integer) getPropertyValue(record, DataType.INTEGER, AudioWireRecordProperties.SAMPLE_SIZE);
            Integer channels = (Integer) getPropertyValue(record, DataType.INTEGER, AudioWireRecordProperties.CHANNELS);
            Boolean signed = (Boolean) getPropertyValue(record, DataType.BOOLEAN, AudioWireRecordProperties.SIGNED);
            Boolean bigEndian = (Boolean) getPropertyValue(record, DataType.BOOLEAN, AudioWireRecordProperties.BIG_ENDIAN);
            
            requireNonNull(source, "No source found");
            requireNonNull(timestamp, "No timestamp found");
            requireNonNull(audioData, "No audio data found");
            requireNonNull(sampleRate, "No sample rate found");
            requireNonNull(sampleSize, "No sample size found");
            requireNonNull(channels, "No channel count found");
            requireNonNull(signed, "No signed flag found");
            requireNonNull(bigEndian, "No big-endian flag found");
            
            logger.debug("Creating AudioFormat with {}Hz, {}bit, {} channels", sampleRate, sampleSize, channels);
            AudioFormat audioFormat = new AudioFormat(sampleRate,
            										  sampleSize,
            										  channels,
            										  signed,
            										  bigEndian);
            logger.debug("Creating ByteArrayInputStream");
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            Integer samples = audioData.length * 8 / sampleSize;
            logger.debug("Creating AudioInputStream");
            AudioInputStream ais = new AudioInputStream(bais, audioFormat, samples);
            String path = this.options.getOutputFilePath();
            String filename = source + "_" +
                    Instant.ofEpochMilli(timestamp).atOffset(ZoneOffset.UTC).toString() +
					  ".wav";
            String pathname = path + "/" + filename;
                              
            logger.debug("Writing {} bytes ({} samples) to {}...", audioData.length, samples, pathname);
            try {
				AudioSystem.write(ais,  AudioFileFormat.Type.WAVE, new File (pathname));
			} catch (IOException e) {
				logger.error("Could not write audio file");
			}
            logger.debug("Writing...Done");
            
            final Map<String, TypedValue<?>> properties = new HashMap<String, TypedValue<?>>(record.getProperties());
        	properties.remove(AudioWireRecordProperties.AUDIO_DATA);
        	properties.put(AudioWireRecordProperties.PATH, TypedValues.newStringValue(path));
        	properties.put(AudioWireRecordProperties.FILENAME, TypedValues.newStringValue(filename));
        	final WireRecord audioCollectorWireRecord = new WireRecord(properties);
        	logger.debug("Creating output record for source {}",
        			source);
        	audioFileWriterWireRecords.add(audioCollectorWireRecord);
        }
		Integer numberOfRecords = audioFileWriterWireRecords.size();
		logger.debug("Emitting {} record(s)...", numberOfRecords);
		if (numberOfRecords > 0) {
	    	wireSupport.emit(audioFileWriterWireRecords);
		}
		logger.debug("Emitting...done");
	}

	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioFileWriterOptions(properties);
    }
	
	private Object getPropertyValue(WireRecord record, DataType expectedType, String propertyName) {
		TypedValue<?> property = record.getProperties().get(propertyName);
		if (property.getType() == expectedType) {
			return property.getValue();
		} else {
			return null;
		}
	}

	@Override
    public Object polled(Wire wire) {
        return this.wireSupport.polled(wire);
    }

    @Override
    public void consumersConnected(Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
    }
}
