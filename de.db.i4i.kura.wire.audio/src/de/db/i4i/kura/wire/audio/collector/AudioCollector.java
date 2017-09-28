package de.db.i4i.kura.wire.audio.collector;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.isNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private long lastReadTimestamp = System.currentTimeMillis();
	private double t = 0;

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
        logger.debug("Activating AudioCollector... Done");
	}
	
	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioCollector...");
        logger.debug("Deactivating AudioCollector... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioCollector...");
        this.extractProperties(properties);
        logger.debug("Updating AudioCollector... Done");
	}
	
	@Override
	public void onWireReceive(WireEnvelope wireEnvelope) {
		requireNonNull(wireEnvelope, "Wire envelope must not be null");
		logger.debug("Received wire envelope from {}", wireEnvelope.getEmitterPid());

    	int a = this.available();
    	logger.debug("Available bytes: {}", a);
    	
    	long now = System.currentTimeMillis();
    	long timeElapsed = 1000 * (a * 8 / this.options.getSampleSize()) / this.options.getSampleRate().longValue();
    	logger.debug("Time elapsed since last received envelope: {}ms", timeElapsed);
    	byte[] b = new byte[a];
    	int r = this.read(b, 0, a);
    	logger.debug("Read {} of {} available bytes", r, a);

    	final Map<String, TypedValue<?>> properties = new HashMap<>();
    	properties.put(AudioWireRecordProperties.SOURCE, TypedValues.newStringValue(this.options.getKuraServicePid()));
    	properties.put(AudioWireRecordProperties.TIMESTAMP, TypedValues.newLongValue(now - timeElapsed));
    	properties.put(AudioWireRecordProperties.AUDIO_DATA, TypedValues.newByteArrayValue(b));
    	properties.put(AudioWireRecordProperties.BIG_ENDIAN, TypedValues.newBooleanValue(this.options.isBigEndian()));
    	properties.put(AudioWireRecordProperties.CHANNELS, TypedValues.newIntegerValue(this.options.getChannels()));
    	properties.put(AudioWireRecordProperties.SAMPLE_RATE, TypedValues.newFloatValue(this.options.getSampleRate()));
    	properties.put(AudioWireRecordProperties.SAMPLE_SIZE, TypedValues.newIntegerValue(this.options.getSampleSize()));
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
    
    private int available() {
    	long now = System.currentTimeMillis();
    	long timeElapsed = now - this.lastReadTimestamp;
    	this.lastReadTimestamp = now;
    	Long available = this.options.getSampleSize() * this.options.getSampleRate().longValue() * timeElapsed / 8000;
    	return Math.round(available.intValue() / 2) * 2;
    }
    
    private int read(byte[] b, int off, Integer len) {
    	double amp = 12000;
    	double f = 400;
    	double sr = this.options.getSampleRate().doubleValue();
    	for (int i = 0; i < len; i += 2) {
    		Integer value = this.waveform(this.t, amp, f);
    		ByteBuffer dbuf = ByteBuffer.allocate(2);
    		dbuf.putShort(value.shortValue());
    		byte[] bytes = dbuf.array();
    		b[i + off] = bytes[1];
    		b[i + 1 + off] = bytes[0];
    		t += 1 / sr;
    	}
    	return len;
    }
    
    private int waveform(double t, double amp, double f) {
    	double pi = 3.1415926536;
    	Double result = amp * Math.sin(2 * pi * f * t);
    	return result.intValue();
    }
}
