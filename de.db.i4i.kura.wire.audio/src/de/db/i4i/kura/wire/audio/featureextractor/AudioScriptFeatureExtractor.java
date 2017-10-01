package de.db.i4i.kura.wire.audio.featureextractor;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraException;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.core.util.ProcessUtil;
import org.eclipse.kura.core.util.SafeProcess;
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

public class AudioScriptFeatureExtractor implements ConfigurableComponent, WireEmitter, WireReceiver {
	
	private static final Logger logger = LoggerFactory.getLogger(AudioScriptFeatureExtractor.class);
	
    private volatile WireHelperService wireHelperService;
	private WireSupport wireSupport;
	
	private AudioScriptFeatureExtractorOptions options;

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
		logger.debug("Activating AudioFeatureExtractor...");
		wireSupport = this.wireHelperService.newWireSupport(this);
        this.extractProperties(properties);
        logger.debug("Activating AudioFeatureExtractor... Done");
	}
	
	protected synchronized void deactivate() {
		logger.debug("Deactivating AudioFeatureExtractor...");
        logger.debug("Deactivating AudioFeatureExtractor... Done");
	}
	
	public synchronized void updated(final Map<String, Object> properties) {
		logger.debug("Updating AudioFeatureExtractor...");
        this.extractProperties(properties);
        logger.debug("Updating AudioFeatureExtractor... Done");
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
		
		String scriptPathName = this.options.getScriptPath() + "/" + this.options.getScriptFilename();
		logger.debug("Script path name: {}", scriptPathName);
		
		final List<WireRecord> audioFeatureExtractorWireRecords = new ArrayList<>();
		for (WireRecord record : wireEnvelope.getRecords()) {
			
			final Map<String, TypedValue<?>> properties = new HashMap<String, TypedValue<?>>(record.getProperties());
			String source = (String) getPropertyValue(record, DataType.STRING, AudioWireRecordProperties.SOURCE);
			String audioFilePath = (String) getPropertyValue(record, DataType.STRING, AudioWireRecordProperties.PATH);
			String audioFilename = (String) getPropertyValue(record, DataType.STRING, AudioWireRecordProperties.FILENAME);
			logger.debug("Audio file path: {}", audioFilePath);
			logger.debug("Audio filename: {}", audioFilename);
			String inputFileArgument = "--inputfile=" + audioFilePath + "/" + audioFilename;
			
			logger.debug("Arguments: {}", inputFileArgument);
			
			SafeProcess process = null;
			BufferedReader br = null;
			final String[] command = { scriptPathName, inputFileArgument };
			logger.debug("Created command: {} {}", command[0], command[1]);
	
			try {
				process = ProcessUtil.exec(command);
				br = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line = null;
	
				logger.debug("Reading result...");
				while ((line = br.readLine()) != null) {
					logger.debug("--- " + line);
					if (line.contains("command not found")) {
						logger.error("Resetting Command Not Found");
						throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
					}
					String[] result = line.split("=");
					String propertyName = result[0];
					TypedValue<Double> propertyValue = TypedValues.newDoubleValue(Double.parseDouble(result[1]));
					requireNonNull(propertyName, "No valid property name found");
					requireNonNull(propertyValue, "No valid property value found");
					properties.put(propertyName, propertyValue);
		        	logger.debug("Creating output record for source {}",
		        			source);
				}
				
				br = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				line = null;
				logger.debug("Reading errors...");
				while ((line = br.readLine()) != null) {
					logger.debug("--- " + line);
					if (line.contains("command not found")) {
						logger.error("Resetting Command Not Found");
						throw new KuraException(KuraErrorCode.OPERATION_NOT_SUPPORTED);
					}
				}
	
				logger.debug("Executing process...Done");
			} catch (final Exception e) {
				logger.error("Could not execute process", e);
			} finally {
				try {
					logger.debug("Closing Buffered Reader and destroying Process", process);
					br.close();
					process.destroy();
				} catch (final IOException e) {
					logger.error("Error closing read buffer", e);
				}
			}
			final WireRecord audioFeatureExtractorWireRecord = new WireRecord(properties);
			audioFeatureExtractorWireRecords.add(audioFeatureExtractorWireRecord);
		}
		Integer numberOfRecords = audioFeatureExtractorWireRecords.size();
		logger.debug("Envelope took {}ms to process", System.currentTimeMillis() - envelopeTimer);
		logger.debug("Emitting {} record(s)...", numberOfRecords);
		if (numberOfRecords > 0) {
	    	wireSupport.emit(audioFeatureExtractorWireRecords);
		}
		logger.debug("Emitting...done");
	}

	private void extractProperties(final Map<String, Object> properties) {
        requireNonNull(properties, "Properties cannot be null");
        this.options = new AudioScriptFeatureExtractorOptions(properties);
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
