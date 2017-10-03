package de.db.i4i.kura.wire.audio.featurematcher;

import static de.db.i4i.kura.wire.audio.AudioWireRecordProperties.ANOMALY_CLASS_DESCRIPTION;
import static de.db.i4i.kura.wire.audio.AudioWireRecordProperties.ANOMALY_CLASS_ID;
import static de.db.i4i.kura.wire.audio.AudioWireRecordProperties.DISTORTION;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.kura.KuraRuntimeException;
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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;

public class AudioFeatureMatcher implements ConfigurableComponent, WireEmitter, WireReceiver {
	
	private static final Logger logger = LoggerFactory.getLogger(AudioFeatureMatcher.class);
	
    private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioFeatureMatcherOptions options;
	private List<Codebook> codebooks;

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
		logger.debug("Activating AudioFeatureMatcher...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        this.codebooks = readCodebooksFromConfig();
        logger.debug("Activating AudioFeatureMatcher... Done");
	}
	
	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioFeatureMatcher...");
        logger.debug("Deactivating AudioFeatureMatcher... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioFeatureMatcher...");
        this.extractProperties(properties);
        this.codebooks = readCodebooksFromConfig();
        logger.debug("Updating AudioFeatureMatcher... Done");
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
		
		final List<WireRecord> audioFeatureMatcherWireRecords = new ArrayList<>();
		for (WireRecord record : wireEnvelope.getRecords()) {
			
			final Map<String, TypedValue<?>> properties = new HashMap<String, TypedValue<?>>(record.getProperties());
			Map<String, Double> featureVector = new HashMap<String, Double>();
			for (String propertyName : properties.keySet()) {
				if (propertyName.startsWith("feature_")) {
					featureVector.put(propertyName, (Double) getPropertyValue(record, DataType.DOUBLE, propertyName));
				}
			}

			Integer anomalyClassId = null;
			String anomalyClassDescription = null;
			Double distortion = Double.MAX_VALUE;
			try {
				for (Codebook codebook : this.codebooks) {
					Double currentDistortion = codebook.getMinDistortion(featureVector);
					if (currentDistortion < distortion) {
						anomalyClassId = codebook.getAnomalyClassId();
						anomalyClassDescription = codebook.getAnomalyClassDescription();
						distortion = currentDistortion;
					}
				}
			} catch (KuraRuntimeException e) {
				logger.error("Could not calculate minimum distortion");
			}
			
			requireNonNull(anomalyClassId, "Could not match feature vector");
			requireNonNull(anomalyClassDescription, "Could not match feature vector");
			properties.put(ANOMALY_CLASS_ID, TypedValues.newIntegerValue(anomalyClassId));
			properties.put(ANOMALY_CLASS_DESCRIPTION, TypedValues.newStringValue(anomalyClassDescription));
			properties.put(DISTORTION, TypedValues.newDoubleValue(distortion));
			
			final WireRecord audioFeatureMatcherWireRecord = new WireRecord(properties);
			audioFeatureMatcherWireRecords.add(audioFeatureMatcherWireRecord);
		}
		Integer numberOfRecords = audioFeatureMatcherWireRecords.size();
		logger.debug("Emitting {} record(s)...", numberOfRecords);
		if (numberOfRecords > 0) {
	    	wireSupport.emit(audioFeatureMatcherWireRecords);
		}
		logger.debug("Emitting...done");
	}

	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioFeatureMatcherOptions(properties);
    }
	
	private Object getPropertyValue(WireRecord record, DataType expectedType, String propertyName) {
		TypedValue<?> property = record.getProperties().get(propertyName);
		if (property.getType() == expectedType) {
			return property.getValue();
		} else {
			return null;
		}
	}
	
	
	private List<Codebook> readCodebooksFromConfig() {
		List<Codebook> codebooks = new ArrayList<Codebook>();
		String codebookPathName = this.options.getCodebookPath() + "/" + this.options.getCodebookFilename();
		FileInputStream fis;
		try {
			fis = new FileInputStream(codebookPathName);
			InputStreamReader reader = new InputStreamReader(fis);
			JsonObject jsonObject = Json.parse(reader).asObject();
			JsonArray jsonCodebooks = jsonObject.get("codebooks").asArray();
			for (JsonValue jsonCodebook : jsonCodebooks) {
				JsonObject anomalyClass = jsonCodebook.asObject().get("anomalyClass").asObject();
				Integer anomalyClassId = anomalyClass.get("id").asInt();
				String anomalyClassDescription = anomalyClass.get("description").asString();
				Codebook codebook = new Codebook(anomalyClassId, anomalyClassDescription, new ArrayList<Map<String, Double>>());
				JsonArray jsonCentroidVectors = jsonCodebook.asObject().get("centroidVectors").asArray();
				for (JsonValue jsonCentroidVector : jsonCentroidVectors) {
					HashMap<String, Double> centroidVector = new HashMap<String, Double>();
					for (Member element : jsonCentroidVector.asObject()) {
						String key = element.getName();
						Double value = element.getValue().asDouble();
						centroidVector.put(key, value);
					}
					codebook.addVector(centroidVector);
				}
				codebooks.add(codebook);
				logger.debug("Added codebook: {}", codebook.toString());
			}
			fis.close();
			return codebooks;
		} catch (FileNotFoundException e) {
			logger.error("Could not find codebook file {}", codebookPathName);
			return null;
		} catch (IOException e) {
			logger.error("Could not close codebook file {}", codebookPathName);
			return null;
		} catch (Exception e) {
			logger.error("Could not parse codebook file {}", codebookPathName);
			return null;
		}
	}
}
