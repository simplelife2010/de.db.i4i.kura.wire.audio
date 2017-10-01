package de.db.i4i.kura.wire.audio.featureextractor;

import static de.db.i4i.kura.wire.audio.AudioWireUtils.byteArrayToDoubleArray;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import jAudioFeatureExtractor.AudioFeatures.MFCC;

public class AudioMFCCFeatureExtractor implements WireEmitter, ConfigurableComponent, WireReceiver {

private static final Logger logger = LoggerFactory.getLogger(AudioMFCCFeatureExtractor.class);
	
    private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioMFCCFeatureExtractorOptions options;

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
		logger.debug("Activating AudioMFCCFeatureExtractor...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        logger.debug("Activating AudioMFCCFeatureExtractor... Done");
	}
	
	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioMFCCFeatureExtractor...");
        logger.debug("Deactivating AudioMFCCFeatureExtractor... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioMFCCFeatureExtractor...");
        this.extractProperties(properties);
        logger.debug("Updating AudioMFCCFeatureExtractor... Done");
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
		long envelopeTimer = System.currentTimeMillis();
		logger.debug("Received wire envelope with {} record(s) from {}", wireEnvelope.getRecords().size(),
				wireEnvelope.getEmitterPid());
		
		final List<WireRecord> audioMFCCFeatureExtractorWireRecords = new ArrayList<>();
		for (WireRecord record : wireEnvelope.getRecords()) {
			
			logger.debug("Extracting properties from record...");
			final Map<String, TypedValue<?>> properties = new HashMap<String, TypedValue<?>>(record.getProperties());
			Float sampleRate = (Float) this.getPropertyValue(record, DataType.FLOAT, AudioWireRecordProperties.SAMPLE_RATE);
			logger.debug("Extracting properties from record...Done");
			try {
				double[] msFeature = byteArrayToDoubleArray(
						(byte[]) this.getPropertyValue(
								record,
								DataType.BYTE_ARRAY,
								AudioWireRecordProperties.MAGNITUDE_SPECTRUM));
				MFCC mfcc = new MFCC();
				double[][] otherFeatureValues = new double[1][msFeature.length];
				otherFeatureValues[0] = msFeature;
				double[] mfccFeature = mfcc.extractFeature(null, sampleRate.doubleValue(), otherFeatureValues);
				for (Integer i = 0; i < mfccFeature.length; i++) {
					properties.put("feature_mfcc_coeff_" + i.toString(), TypedValues.newDoubleValue(mfccFeature[i]));
				}
			} catch (Exception e) {
				logger.error("Could not extract feature:", e);
			}
			
			properties.remove(AudioWireRecordProperties.MAGNITUDE_SPECTRUM);
			
			final WireRecord audioMFCCFeatureExtractorWireRecord = new WireRecord(properties);
			audioMFCCFeatureExtractorWireRecords.add(audioMFCCFeatureExtractorWireRecord);
		}
		Integer numberOfRecords = audioMFCCFeatureExtractorWireRecords.size();
		logger.debug("Envelope took {}ms to process", System.currentTimeMillis() - envelopeTimer);
		logger.debug("Emitting {} record(s)...", numberOfRecords);
		if (numberOfRecords > 0) {
	    	wireSupport.emit(audioMFCCFeatureExtractorWireRecords);
		}
		logger.debug("Emitting...done");
	}

	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioMFCCFeatureExtractorOptions(properties);
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
