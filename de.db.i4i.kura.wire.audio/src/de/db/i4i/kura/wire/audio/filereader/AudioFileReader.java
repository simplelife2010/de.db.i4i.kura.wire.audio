package de.db.i4i.kura.wire.audio.filereader;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraRuntimeException;
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


public class AudioFileReader implements ConfigurableComponent, WireEmitter, WireReceiver {

private static final Logger logger = LoggerFactory.getLogger(AudioFileReader.class);
	
    private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioFileReaderOptions options;
	private long lastReadTimestamp;
	private AudioInputStream audioInputStream = null;

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
		logger.debug("Activating AudioFileReader...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        lastReadTimestamp = System.currentTimeMillis();
        logger.debug("Activating AudioFileReader... Done");
	}
	
	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioFileReader...");
        logger.debug("Deactivating AudioFileReader... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioFileReader...");
        this.extractProperties(properties);
        lastReadTimestamp = System.currentTimeMillis();
        logger.debug("Updating AudioFileReader... Done");
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
		logger.debug("Received wire envelope from {}", wireEnvelope.getEmitterPid());
		
		String pathname = this.options.getPath() + "/" + this.options.getFilename();
		if (isNull(this.audioInputStream)) {
			try {
				this.audioInputStream = AudioSystem.getAudioInputStream(
						new File(pathname));
			} catch (UnsupportedAudioFileException | IOException e) {
				logger.error("Could not get audio input stream for {}", pathname, e);
				throw new KuraRuntimeException(KuraErrorCode.CONFIGURATION_ERROR);
			}
		}

    	int availableBytes = this.available();
    	logger.debug("Available bytes: {}", availableBytes);
    	
    	AudioFormat audioFormat = this.audioInputStream.getFormat();
    	Integer sampleSize = audioFormat.getSampleSizeInBits();
    	Float sampleRate = audioFormat.getSampleRate();
    	Boolean bigEndian = audioFormat.isBigEndian();
    	Integer channels = audioFormat.getChannels();
    	AudioFormat.Encoding encoding = audioFormat.getEncoding();
    	Boolean signed;
    	if (encoding == AudioFormat.Encoding.PCM_SIGNED) {
    		signed = true;
    	} else if (encoding == AudioFormat.Encoding.PCM_UNSIGNED) {
    		signed = false;
    	} else {
    		logger.error("Encoding {} not supported", encoding.toString());
    		throw new KuraRuntimeException(KuraErrorCode.DECODER_ERROR);
    	}
    	
    	long now = System.currentTimeMillis();
    	long timeElapsed = 1000 * (availableBytes * 8 / sampleSize) / sampleRate.longValue();
    	logger.debug("Time elapsed since last received envelope: {}ms", timeElapsed);
    	
    	byte[] audioData = new byte[availableBytes];
    	int readBytes;
		try {
			readBytes = this.audioInputStream.read(audioData, 0, availableBytes);
		} catch (IOException e) {
			logger.error("Could not read from input stream for {}", pathname, e);
			throw new KuraRuntimeException(KuraErrorCode.CONFIGURATION_ERROR);
		}
    	logger.debug("Read {} of {} available bytes", readBytes, availableBytes);
    	int bytesNotRead = availableBytes - readBytes;
    	if (bytesNotRead > 0) {
    		logger.debug("EOF reached, continue reading from start of file");
    		try {
				this.audioInputStream = AudioSystem.getAudioInputStream(
						new File(pathname));
				readBytes = this.audioInputStream.read(audioData, readBytes, bytesNotRead);
			} catch (Exception e) {
				logger.error("Could not read from input stream for {}", pathname, e);
				throw new KuraRuntimeException(KuraErrorCode.CONFIGURATION_ERROR);
			}
    		logger.debug("Read {} of {} remaining bytes", readBytes, bytesNotRead);
    	}
    	
    	final Map<String, TypedValue<?>> properties = new HashMap<>();
    	properties.put(AudioWireRecordProperties.SOURCE, TypedValues.newStringValue(this.options.getKuraServicePid()));
    	properties.put(AudioWireRecordProperties.TIMESTAMP, TypedValues.newLongValue(now - timeElapsed));
    	properties.put(AudioWireRecordProperties.AUDIO_DATA, TypedValues.newByteArrayValue(audioData));
    	properties.put(AudioWireRecordProperties.BIG_ENDIAN, TypedValues.newBooleanValue(bigEndian));
    	properties.put(AudioWireRecordProperties.CHANNELS, TypedValues.newIntegerValue(channels));
    	properties.put(AudioWireRecordProperties.SAMPLE_RATE, TypedValues.newFloatValue(sampleRate));
    	properties.put(AudioWireRecordProperties.SAMPLE_SIZE, TypedValues.newIntegerValue(sampleSize));
    	properties.put(AudioWireRecordProperties.SIGNED, TypedValues.newBooleanValue(signed));
    	
    	final WireRecord audioFileReaderWireRecord = new WireRecord(properties);
    	final List<WireRecord> audioFileReaderWireRecords = new ArrayList<>();
    	audioFileReaderWireRecords.add(audioFileReaderWireRecord);
        logger.debug("Emitting {} record(s)...", audioFileReaderWireRecords.size());
    	wireSupport.emit(audioFileReaderWireRecords);
    	logger.debug("Emitting...done");
	}
	
	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioFileReaderOptions(properties);
    }

	private int available() {
		requireNonNull(this.audioInputStream, "Audio input stream must not be null");
    	long now = System.currentTimeMillis();
    	long timeElapsed = now - this.lastReadTimestamp;
    	this.lastReadTimestamp = now;
    	AudioFormat audioFormat = this.audioInputStream.getFormat();
    	Long available = audioFormat.getSampleSizeInBits() * (long) audioFormat.getSampleRate() * timeElapsed / 8000;
    	return Math.round(available.intValue() / 2) * 2;
    }
}
