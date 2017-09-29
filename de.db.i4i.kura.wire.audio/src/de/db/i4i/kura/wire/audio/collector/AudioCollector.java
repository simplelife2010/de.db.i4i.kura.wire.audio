package de.db.i4i.kura.wire.audio.collector;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import org.eclipse.kura.configuration.ConfigurableComponent;
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

public class AudioCollector implements WireEmitter, WireReceiver, ConfigurableComponent {
	
	private static final Logger logger = LoggerFactory.getLogger(AudioCollector.class);
	
    private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioCollectorOptions options;
	private TargetDataLine targetDataLine;

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
		logger.debug("Activating AudioCollector...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        startRecording();
        logger.debug("Activating AudioCollector... Done");
	}

	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioCollector...");
		stopRecording();
        logger.debug("Deactivating AudioCollector... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioCollector...");
		stopRecording();
        this.extractProperties(properties);
        startRecording();
        logger.debug("Updating AudioCollector... Done");
	}
	
	@Override
	public void onWireReceive(WireEnvelope wireEnvelope) {
		requireNonNull(wireEnvelope, "Wire envelope must not be null");
		logger.debug("Received wire envelope from {}", wireEnvelope.getEmitterPid());

		int bufferSize = this.targetDataLine.getBufferSize();
		byte[] audioData = new byte[bufferSize];
		int availableBytes = this.targetDataLine.available();
		int bytesRead = this.targetDataLine.read(audioData, 0, availableBytes);
		long now = System.currentTimeMillis();
		logger.debug("Read {} of {} available bytes into buffer of size {}", bytesRead, availableBytes, bufferSize);
		if (availableBytes == bufferSize) {
			logger.warn("Buffer size exceeded, some audio data was lost");
		}
    	
		Float sampleRate = this.options.getSampleRate();
		Integer sampleSize = this.options.getSampleSize();

    	final Map<String, TypedValue<?>> properties = new HashMap<>();
    	properties.put(AudioWireRecordProperties.SOURCE, TypedValues.newStringValue(this.options.getKuraServicePid()));
    	properties.put(AudioWireRecordProperties.TIMESTAMP, TypedValues.newLongValue(
    			now - bytesRead * 8000 / (sampleSize * sampleRate.longValue())));
    	properties.put(AudioWireRecordProperties.AUDIO_DATA, TypedValues.newByteArrayValue(audioData));
    	properties.put(AudioWireRecordProperties.BIG_ENDIAN, TypedValues.newBooleanValue(this.options.isBigEndian()));
    	properties.put(AudioWireRecordProperties.CHANNELS, TypedValues.newIntegerValue(this.options.getChannels()));
    	properties.put(AudioWireRecordProperties.SAMPLE_RATE, TypedValues.newFloatValue(sampleRate));
    	properties.put(AudioWireRecordProperties.SAMPLE_SIZE, TypedValues.newIntegerValue(sampleSize));
    	properties.put(AudioWireRecordProperties.SIGNED, TypedValues.newBooleanValue(this.options.isSigned()));
    	
    	final WireRecord audioCollectorWireRecord = new WireRecord(properties);
    	final List<WireRecord> audioCollectorWireRecords = new ArrayList<>();
    	audioCollectorWireRecords.add(audioCollectorWireRecord);
        logger.debug("Emitting {} record(s)...", audioCollectorWireRecords.size());
    	wireSupport.emit(audioCollectorWireRecords);
    	logger.debug("Emitting...done");
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

	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioCollectorOptions(properties);
    }
	
	
	private void startRecording() {
		AudioFormat audioFormat = new AudioFormat(
				this.options.getSampleRate(),
				this.options.getSampleSize(),
				this.options.getChannels(),
				this.options.isSigned(),
				this.options.isBigEndian());
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
		if (AudioSystem.isLineSupported(info)) {
			try {
				logger.debug("Obtaining line...");
				this.targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
				logger.debug("Obtaining line...Done");
				logger.debug("Opening line...");
				this.targetDataLine.open(audioFormat, this.options.getBufferSize());
				logger.debug("Opening line...Done");
				logger.debug("Starting line...");
				this.targetDataLine.start();
				logger.debug("Starting line...Done");
			} catch (LineUnavailableException e) {
				logger.error("Cannot create/open line: {}", audioFormat.toString());
			}
		} else {
			logger.error("Line is not supported: {}", audioFormat.toString());
			this.targetDataLine = null;
		}
	}
	
	private void stopRecording() {
		logger.debug("Closing line...");
		this.targetDataLine.stop();
		this.targetDataLine.close();
		this.targetDataLine = null;
		logger.debug("Closing line...Done");
	}
}
