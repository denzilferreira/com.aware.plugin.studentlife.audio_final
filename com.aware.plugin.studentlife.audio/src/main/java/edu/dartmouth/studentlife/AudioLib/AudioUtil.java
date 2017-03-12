package edu.dartmouth.studentlife.AudioLib;


public class AudioUtil {
	public static double getAmplitude(short[] buffer) {
		int readSize = buffer.length;
		double sum = 0;
		double amplitude = 0;
		
		for (int i = 0; i < readSize; i++) {
			sum += buffer[i] * buffer[i];
		}
		if (readSize > 0) {
			amplitude = sum / readSize;
		}
		
		return Math.sqrt(amplitude);
	}
}
