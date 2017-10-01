package de.db.i4i.kura.wire.audio.concatenator;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;

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

public class AudioConcatenator implements WireEmitter, WireReceiver, ConfigurableComponent {
	
private static final Logger logger = LoggerFactory.getLogger(AudioConcatenator.class);
	
    private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioConcatenatorOptions options;
	private Map<String, ByteBuffer> audioBuffers;
	
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
	
	protected synchronized void activate(final Map<String, Object> properties) {
		logger.debug("Activating AudioConcatenator...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        this.audioBuffers = new HashMap<String, ByteBuffer>();
        logger.debug("Activating AudioConcatenator... Done");
	}
	
	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioConcatenator...");
        logger.debug("Deactivating AudioConcatenator... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioConcatenator...");
        this.extractProperties(properties);
        this.audioBuffers = new HashMap<String, ByteBuffer>();
        logger.debug("Updating AudioConcatenator... Done");
	}

	@Override
    public Object polled(Wire wire) {
        return this.wireSupport.polled(wire);
    }

    @Override
    public void consumersConnected(Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
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
		long envelopeTimer = System.currentTimeMillis();
		
		final List<WireRecord> audioCollectorRecords = new ArrayList<>();
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
            
            AudioFormat audioFormat = new AudioFormat(sampleRate,
					  sampleSize,
					  channels,
					  signed,
					  bigEndian);
            String key = source + ", " + audioFormat.toString();
            ByteBuffer audioBuffer;
            Integer concatenationDuration = this.options.getConcatenationDuration();
            Integer bytesPerSecond = sampleRate.intValue() * sampleSize / 8;
            Integer numberOfBytes = concatenationDuration * bytesPerSecond;
            byte[] concatenatedAudioData = new byte[numberOfBytes];
            if (this.audioBuffers.containsKey(key)) {
            	audioBuffer = audioBuffers.get(key);
            } else {
            	audioBuffer = ByteBuffer.allocate(5 * numberOfBytes);
            	audioBuffers.put(key, audioBuffer);
            }
            try {
				audioBuffer.put(audioData);
			} catch (BufferOverflowException e) {
				logger.warn("Source {}: Buffer capacity ({} bytes) exceeded trying to put {} bytes",
						source, audioBuffer.capacity(), audioData.clone().length);
			}
            logger.debug("Source {}: Buffer contains {} seconds of audio ({} bytes)",
        			source,
        			audioBuffer.position() * 8 / sampleSize / sampleRate, audioBuffer.position());
            if (audioBuffer.position() >= numberOfBytes) {
            	logger.debug("Target buffer size reached. Flipping buffer.");
            	audioBuffer.flip();
            	logger.debug("Reading {} bytes from buffer", numberOfBytes);
            	audioBuffer.get(concatenatedAudioData, 0, numberOfBytes);
            	logger.debug("Compacting buffer");
            	audioBuffer.compact();
            	final Map<String, TypedValue<?>> properties = new HashMap<String, TypedValue<?>>(record.getProperties());
            	properties.replace(AudioWireRecordProperties.AUDIO_DATA, TypedValues.newByteArrayValue(concatenatedAudioData));
            	// Guess timestamp, this will fail if wire records were lost
            	long newTimestamp = timestamp - (concatenationDuration * 1000) +
            			audioBuffer.position() * 8000 / sampleSize / sampleRate.longValue();
            	properties.replace(AudioWireRecordProperties.TIMESTAMP, TypedValues.newLongValue(newTimestamp));
            	final WireRecord audioCollectorWireRecord = new WireRecord(properties);
            	logger.debug("Creating output record for source {}, {} seconds ({} bytes) of audio left in buffer",
            			source,
            			audioBuffer.position() * 8 / sampleSize / sampleRate,
            			audioBuffer.position());
            	audioCollectorRecords.add(audioCollectorWireRecord);
            }
        }
		Integer numberOfRecords = audioCollectorRecords.size();
		logger.debug("Envelope took {}ms to process", System.currentTimeMillis() - envelopeTimer);
		logger.debug("Emitting {} record(s)...", numberOfRecords);
		if (numberOfRecords > 0) {
	    	wireSupport.emit(audioCollectorRecords);
		}
		logger.debug("Emitting...done");
	}

	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioConcatenatorOptions(properties);
    }
	
	private Object getPropertyValue(WireRecord record, DataType expectedType, String propertyName) {
		TypedValue<?> property = record.getProperties().get(propertyName);
		if (property.getType() == expectedType) {
			return property.getValue();
		} else {
			return null;
		}
	}
}
