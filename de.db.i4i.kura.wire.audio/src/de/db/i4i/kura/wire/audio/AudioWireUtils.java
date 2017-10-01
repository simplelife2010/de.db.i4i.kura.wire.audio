package de.db.i4i.kura.wire.audio;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class AudioWireUtils {

	public static double[] byteArrayToDoubleArray(byte[] byteArray) {
		double[] doubleArray = new double[byteArray.length / 8];
		for (int i = 0; i < doubleArray.length; i++) {
			doubleArray[i] = byteArrayToDouble(Arrays.copyOfRange(byteArray, i * 8, (i + 1) * 8));
		}
		return doubleArray;
	}
	
	public static byte[] doubleArrayToByteArray(double[] doubleArray) {
		byte[] byteArray = new byte[doubleArray.length * 8];
		for (int i = 0; i < doubleArray.length; i++) {
			byte[] byteArrayValue = doubleToByteArray(doubleArray[i]);
			for (int j = 0; j < 8; j++) {
				byteArray[i * 8 + j] = byteArrayValue[j];
			}
		}
		return byteArray;
	}
	
	public static double byteArrayToDouble(byte[] byteArray) {
		return ByteBuffer.wrap(byteArray).getDouble();
	}
	
	public static byte[] doubleToByteArray(double doubleValue) {
		return ByteBuffer.allocate(8).putDouble(doubleValue).array();
	}
}
