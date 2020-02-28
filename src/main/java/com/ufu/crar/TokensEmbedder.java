package com.ufu.crar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.ufu.crar.util.CrarUtils;

/**
 * Embed tokens with Word2Vec, and vectorized data with embedded tokens.
 * 
 * @author kui.liu
 *
 */
public class TokensEmbedder {
	String inputPath;
	String outputPath;
	
	/**
	 * Merge training data and testing data.
	 */
	public void mergeData(boolean mergeData) {
		String methodTokens = inputPath + "SelectedData/SelectedMethodTokens.txt";
		String embeddingInputData = inputPath + "embedding/inputData.txt";
		
		// merge source code tokens of fixed violations.
		FileHelper.outputToFile(embeddingInputData, FileHelper.readFile(methodTokens), false);
		if (mergeData) {
			String renamedTokens = inputPath + "RenamedMethods/MethodTokens.txt";
			FileHelper.outputToFile(embeddingInputData, FileHelper.readFile(renamedTokens), true);
		}
	}
	
	/**
	 * Embed tokens with Word2Vec.
	 */
	public void embedTokens() {
		String embeddingInputData = inputPath + "embedding/inputData.txt";
		String embeddedTokensFile = inputPath + "embedding/embeddedTokens.txt";
		Word2VecEncoder encoder = new Word2VecEncoder();
		int windowSize = 4;
		encoder.setWindowSize(windowSize);
		try {
			File inputFile = new File(embeddingInputData);
			int minWordFrequency = 1;
			int layerSize = 300;
			
			encoder.embedTokens(inputFile, minWordFrequency, layerSize, embeddedTokensFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void vectorizedData(boolean benchMark) throws IOException {
		String embeddedTokensFile = inputPath + "embedding/embeddedTokens.txt";
		Map<String, String> embeddedTokens = readEmbeddedTokens(embeddedTokensFile);
		File[] files = new File(inputPath + "TrainingData/").listFiles();
		File trainingDataFile = null;
		int maxSize = 0;
		for (File file : files) {
			String fileName = file.getName();
			if (fileName.startsWith("Tokens_MaxSize=")) {
				maxSize = Integer.parseInt(fileName.substring("Tokens_MaxSize=".length(), fileName.lastIndexOf(".txt")));
				trainingDataFile = file;
			}
		}
		
		StringBuilder zeroVector = new StringBuilder();
		int size = CrarApp.SIZE_EMBEDDING - 1;
		for (int i = 0; i < size; i ++) {
			zeroVector.append("0,");
		}
		zeroVector.append("0");
		System.out.println(maxSize);
		
		File idDataFileInput = new File(inputPath + "SelectedData/SelectedMethodTokens_id.txt");
		vectorizeTokenVectorId(idDataFileInput, embeddedTokens, maxSize, zeroVector, outputPath + "SelectedMethodTokens_id_output.txt");
		
		File testingDataFile = new File(inputPath + "TestingData/" + trainingDataFile.getName());
		vectorizeTokenVector(trainingDataFile, embeddedTokens, maxSize, zeroVector, outputPath + "TrainingData_");
		vectorizeTokenVectorTesting(testingDataFile, embeddedTokens, maxSize, zeroVector, outputPath + "TestingData_");
		if (benchMark) {
			File renamedMethodsFile = new File(inputPath + "RenamedMethods/MethodTokens.txt");
			vectorizeTokenVector(renamedMethodsFile, embeddedTokens, maxSize, zeroVector, outputPath + "RenamedData_");
		}
	}

	public Map<String, String> readEmbeddedTokens(String embeddedTokensFile) throws IOException {
		Map<String, String> embeddedTokens = new HashMap<>();
		File file = new File(embeddedTokensFile);
		FileInputStream fis = null;
		Scanner scanner = null;
		fis = new FileInputStream(file);
		scanner = new Scanner(fis);
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			int firstBlankIndex = line.indexOf(" ");
			String token = line.substring(0, firstBlankIndex);
			String value = line.substring(firstBlankIndex + 1).replaceAll(" ", ",");
			embeddedTokens.put(token, value);
		}
		scanner.close();
		fis.close();
		
		return embeddedTokens;
	}

	public void vectorizeTokenVector(File tokenVectorsFile, Map<String, String> embeddedTokens, int maxSize, StringBuilder zeroVector, String outputFileName) throws IOException {
		outputFileName += tokenVectorsFile.getName().replace(".txt", ".csv");
		
		FileInputStream fis = new FileInputStream(tokenVectorsFile);
		Scanner scanner = new Scanner(fis);
		int vectorSize = 0;
		StringBuilder builder = new StringBuilder();
		int counter = 0;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			List<String> methodBodyTokens = Arrays.asList(line.split(" "));	
			
			SingleVectorizer vecBody = new SingleVectorizer();

			vecBody.vectorize(methodBodyTokens, embeddedTokens, maxSize, zeroVector);
			StringBuilder vectorizedTokenVector = vecBody.numericVector;
			int length = vectorizedTokenVector.toString().trim().split(",").length;
			if (length != vectorSize) {
				System.err.println(length);
				vectorSize = length;
			}
			builder.append(vectorizedTokenVector).append("\n");

			counter ++;
			if (counter % 500 == 0) {
				FileHelper.outputToFile(outputFileName, builder, true);
				builder.setLength(0);
			}
		}
		scanner.close();
		fis.close();
		
		FileHelper.outputToFile(outputFileName, builder, true);
		builder.setLength(0);
	}
	
	public void vectorizeTokenVectorId(File tokenVectorsFile, Map<String, String> embeddedTokens, int maxSize, StringBuilder zeroVector, String outputFileName) throws IOException {
		outputFileName += tokenVectorsFile.getName().replace(".txt", ".csv");
		
		FileInputStream fis = new FileInputStream(tokenVectorsFile);
		Scanner scanner = new Scanner(fis);
		int vectorSize = 0;
		StringBuilder builder = new StringBuilder();
		int counter = 0;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			List<String> methodBodyTokens = Arrays.asList(line.split(" "));
			SingleVectorizer vecBody = new SingleVectorizer();
			
			String id = methodBodyTokens.get(0);
		

			vecBody.vectorize(methodBodyTokens.subList(2,methodBodyTokens.size()), embeddedTokens, maxSize, zeroVector);
			StringBuilder vectorizedTokenVector = vecBody.numericVector;
			int length = vectorizedTokenVector.toString().trim().split(",").length;
			if (length != vectorSize) {
				System.err.println(length);
				vectorSize = length;
			}
			builder.append(id+" >> "+vectorizedTokenVector).append("\n");
			counter ++;
			if (counter % 500 == 0) {
				FileHelper.outputToFile(outputFileName, builder, true);
				builder.setLength(0);
			}
		}
		scanner.close();
		fis.close();
		
		FileHelper.outputToFile(outputFileName, builder, true);
		builder.setLength(0);
	}
	
	public void vectorizeTokenVectorTesting(File tokenVectorsFile, Map<String, String> embeddedTokens, int maxSize, StringBuilder zeroVector, String outputFileName) throws IOException {
		outputFileName += tokenVectorsFile.getName().replace(".txt", ".csv");
		
		FileInputStream fis = new FileInputStream(tokenVectorsFile);
		Scanner scanner = new Scanner(fis);
		int vectorSize = 0;
		StringBuilder builder = new StringBuilder();
		int counter = 0;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			String lineProcess = CrarUtils.processQuery(line,false); 
			List<String> methodBodyTokens = Arrays.asList(lineProcess.split(" "));	
			
			SingleVectorizer vecBody = new SingleVectorizer();

			vecBody.vectorize(methodBodyTokens, embeddedTokens, maxSize, zeroVector);
			StringBuilder vectorizedTokenVector = vecBody.numericVector;
			int length = vectorizedTokenVector.toString().trim().split(",").length;
			if (length != vectorSize) {
				System.err.println(length);
				vectorSize = length;
			}
			builder.append(line+" >> "+vectorizedTokenVector).append("\n");

			counter ++;
			if (counter % 500 == 0) {
				FileHelper.outputToFile(outputFileName, builder, true);
				builder.setLength(0);
			}
		}
		scanner.close();
		fis.close();
		
		FileHelper.outputToFile(outputFileName, builder, true);
		builder.setLength(0);
	}
	
	/**
	 * Single vectorizer of a single token vector.
	 * 
	 * @author kui.liu
	 *
	 */
	public class SingleVectorizer {
		public StringBuilder numericVector = new StringBuilder();
		
		/**
		 * Append symbol "," in each iteration.
		 * 
		 * @param tokenVector
		 * @param embeddedTokens
		 * @param maxSize
		 * @param zeroVector
		 */
		public void vectorize(List<String> tokenVector, Map<String, String> embeddedTokens, int maxSize, StringBuilder zeroVector) {
			int i = 0;
			for (int size = tokenVector.size(); i < size; i ++) {
				String numericVectorOfSingleToken = embeddedTokens.get(tokenVector.get(i));
				if (numericVectorOfSingleToken == null) {
					numericVectorOfSingleToken = zeroVector.toString();
				}
				numericVector.append(numericVectorOfSingleToken);
				if (i < maxSize - 1) {
					numericVector.append(",");
				}
			}
			for (; i < maxSize; i ++) {
				numericVector.append(zeroVector);
				if (i < maxSize - 1) {
					numericVector.append(",");
				}
			}
		}
	}
}