package de.db.i4i.kura.wire.audio.featureextractor;

import static de.db.i4i.kura.wire.audio.AudioWireUtils.doubleArrayToByteArray;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

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
import edu.cmu.sphinx.frontend.DataProcessor;
import edu.cmu.sphinx.frontend.DoubleData;
import edu.cmu.sphinx.frontend.FrontEnd;
import edu.cmu.sphinx.frontend.transform.DiscreteFourierTransform;
import edu.cmu.sphinx.frontend.util.AudioFileDataSource;

public class AudioMagSpectrumFeatureExtractor implements WireEmitter, ConfigurableComponent, WireReceiver {

private static final Logger logger = LoggerFactory.getLogger(AudioMagSpectrumFeatureExtractor.class);
	
    private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioMagSpectrumFeatureExtractorOptions options;

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
		logger.debug("Activating AudioMagSpectrumFeatureExtractor...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        logger.debug("Activating AudioMagSpectrumFeatureExtractor... Done");
	}
	
	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioMagSpectrumFeatureExtractor...");
        logger.debug("Deactivating AudioMagSpectrumFeatureExtractor... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioMagSpectrumFeatureExtractor...");
        this.extractProperties(properties);
        logger.debug("Updating AudioMagSpectrumFeatureExtractor... Done");
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
		logger.debug("Received wire envelope with {} record(s) from {}", wireEnvelope.getRecords().size(),
				wireEnvelope.getEmitterPid());
		long envelopeTimer = System.currentTimeMillis();
		
		final List<WireRecord> audioMagSpectrumFeatureExtractorWireRecords = new ArrayList<>();
		for (WireRecord record : wireEnvelope.getRecords()) {
			
			logger.debug("Extracting properties from record...");
			final Map<String, TypedValue<?>> properties = new HashMap<String, TypedValue<?>>(record.getProperties());
			Float sampleRate = (Float) this.getPropertyValue(record, DataType.FLOAT, AudioWireRecordProperties.SAMPLE_RATE);
			Integer sampleSize = (Integer) this.getPropertyValue(record, DataType.INTEGER, AudioWireRecordProperties.SAMPLE_SIZE);
			Integer channels = (Integer) this.getPropertyValue(record, DataType.INTEGER, AudioWireRecordProperties.CHANNELS);
			Boolean signed = (Boolean) this.getPropertyValue(record, DataType.BOOLEAN, AudioWireRecordProperties.SIGNED);
			Boolean bigEndian = (Boolean) this.getPropertyValue(record, DataType.BOOLEAN, AudioWireRecordProperties.BIG_ENDIAN);
			byte[] audioData = (byte[]) this.getPropertyValue(record, DataType.BYTE_ARRAY, AudioWireRecordProperties.AUDIO_DATA);
			logger.debug("Extracting properties from record...Done");
			AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSize, channels, signed, bigEndian);
			logger.debug("AudioFormat: {}", audioFormat.toString());
			ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
			AudioInputStream ais = new AudioInputStream(bais, audioFormat, audioData.length);
			long featureTimer = System.currentTimeMillis();
			logger.debug("Creating AudioFileDataSource...");
			AudioFileDataSource ds = new AudioFileDataSource(audioData.length, null);
			logger.debug("Creating AudioFileDataSource...Done");
			ds.setInputStream(ais, "source");
			final ArrayList<DataProcessor> pipeline = new ArrayList<DataProcessor>();
			pipeline.add(ds);
			pipeline.add(new DiscreteFourierTransform());
			FrontEnd f = new FrontEnd(pipeline);
			logger.debug("Getting feature from pipeline...");
			double[] msFeature = ((DoubleData) f.getData()).getValues();
			logger.debug("Getting feature from pipeline...Done");
			logger.debug("Extracting magnitude spectrum took {}ms", System.currentTimeMillis() - featureTimer);
			long doubleToByteTimer = System.currentTimeMillis();
			properties.put(AudioWireRecordProperties.MAGNITUDE_SPECTRUM,
					TypedValues.newByteArrayValue(doubleArrayToByteArray(msFeature)));
			logger.debug("Converting magnitude spectrum to byte[] took {}ms", System.currentTimeMillis() - doubleToByteTimer);
			
			properties.remove(AudioWireRecordProperties.AUDIO_DATA);
			final WireRecord audioMagSpectrumFeatureExtractorWireRecord = new WireRecord(properties);
			audioMagSpectrumFeatureExtractorWireRecords.add(audioMagSpectrumFeatureExtractorWireRecord);
		}
		Integer numberOfRecords = audioMagSpectrumFeatureExtractorWireRecords.size();
		logger.debug("Envelope took {}ms to process", System.currentTimeMillis() - envelopeTimer);
		logger.debug("Emitting {} record(s)...", numberOfRecords);
		if (numberOfRecords > 0) {
	    	wireSupport.emit(audioMagSpectrumFeatureExtractorWireRecords);
		}
		logger.debug("Emitting...done");
		
	}

	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioMagSpectrumFeatureExtractorOptions(properties);
    }
	
	private Object getPropertyValue(WireRecord record, DataType expectedType, String propertyName) {
		logger.debug("Getting property value for {}...", propertyName);
		TypedValue<?> property = record.getProperties().get(propertyName);
		if (property.getType() == expectedType) {
			logger.debug("Got value for {}", propertyName);
			return property.getValue();
		} else {
			logger.debug("Value = null");
			return null;
		}
	}

}
