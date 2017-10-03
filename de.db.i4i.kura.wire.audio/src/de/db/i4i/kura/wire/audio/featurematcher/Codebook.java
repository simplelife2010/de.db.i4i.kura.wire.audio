package de.db.i4i.kura.wire.audio.featurematcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.KuraErrorCode;
import org.eclipse.kura.KuraRuntimeException;

public class Codebook {
	private Integer anomalyClassId;
	private String anomalyClassDescription;
	private List<Map<String, Double>> centroidVectors;
	public Codebook(Integer anomalyClassId, String anomalyClassDescription, List<Map<String, Double>> centroidVectors) {
		this.anomalyClassId = anomalyClassId;
		this.anomalyClassDescription = anomalyClassDescription;
		this.centroidVectors = centroidVectors;
	}
	public Integer getAnomalyClassId() {
		return anomalyClassId;
	}
	public void setAnomalyClassId(Integer anomalyClassId) {
		this.anomalyClassId = anomalyClassId;
	}
	public String getAnomalyClassDescription() {
		return anomalyClassDescription;
	}
	public void setAnomalyClassDescription(String anomalyClassDescription) {
		this.anomalyClassDescription = anomalyClassDescription;
	}
	public List<Map<String, Double>> getCentroidVectors() {
		return centroidVectors;
	}
	public void setCentroidVectors(List<Map<String, Double>> centroidVectors) {
		this.centroidVectors = centroidVectors;
	}
	public void addVector(HashMap<String, Double> centroidVector) {
		this.centroidVectors.add(centroidVector);
	}
	@Override
	public String toString() {
		return "Id: " + anomalyClassId + ", Description: " + anomalyClassDescription + ", Vector(s): " + centroidVectors.size();
	}
	
	public Double getMinDistortion(Map<String, Double> featureVector) throws KuraRuntimeException {
		Double minDistance2 = Double.MAX_VALUE;
		for (Map<String, Double> centroidVector : this.centroidVectors) {
			Double distance2 = distance2(featureVector, centroidVector);
			if (distance2 < minDistance2) {
				minDistance2 = distance2;
			}
		}
		return Math.sqrt(minDistance2);
	}
	
	private Double distance2(Map<String, Double> vector1, Map<String, Double> vector2) throws KuraRuntimeException {
		if (vector1.size() == vector2.size()) {
			Double distance2 = 0.0;
			for (Entry<String, Double> element1 : vector1.entrySet()) {
				distance2 += Math.pow(vector2.get(element1.getKey()) - element1.getValue(), 2.0);
			}
			return distance2;
		} else {
			throw new KuraRuntimeException(KuraErrorCode.INVALID_PARAMETER);
		}
	}
}