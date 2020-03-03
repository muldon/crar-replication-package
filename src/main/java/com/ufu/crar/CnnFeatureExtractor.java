package com.ufu.crar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public class CnnFeatureExtractor {

	int sizeOfVector = 0; // The size of a tokens' vector.
	int sizeOfTokenVec = 0; // The size of an embedded token vector.
	private int batchSize;
	private int sizeOfFeatureVector; // The size of feature vector, which is the extracted features of each instance.

	private final int nChannels = 1; // Number of input channels.
	private final int iterations = 1;// Number of training iterations.
										// Multiple iterations are generally only used when doing full-batch training on
										// very small data sets.
	private int nEpochs = 1; // Number of training epochs
	private int seed = 123;

	private int numOfOutOfLayer1 = 20;
	private int numOfOutOfLayer2 = 50;

	private int outputNum; // The number of possible outcomes

	public CnnFeatureExtractor(int sizeOfVector, int sizeOfTokenVec, int batchSize, int sizeOfFeatureVector) {
		this.sizeOfVector = sizeOfVector;
		this.sizeOfTokenVec = sizeOfTokenVec;
		this.batchSize = batchSize;
		this.sizeOfFeatureVector = sizeOfFeatureVector;
		/*
		 * If the deep learning is unsupervised learning, the number of outcomes is the
		 * size of input vector. If the deep learning is supervised learning, the number
		 * of outcomes is the number of classes.
		 */
		outputNum = sizeOfVector * sizeOfTokenVec;
	}

	public CnnFeatureExtractor(int batchSize) {
		this.batchSize = batchSize;
	}

	public void setNumberOfEpochs(int nEpochs) {
		this.nEpochs = nEpochs;
	}

	public void setSeed(int seed) {
		this.seed = seed;
	}

	public void setNumOfOutOfLayer1(int numOfOutOfLayer1) {
		this.numOfOutOfLayer1 = numOfOutOfLayer1;
	}

	public void setNumOfOutOfLayer2(int numOfOutOfLayer2) {
		this.numOfOutOfLayer2 = numOfOutOfLayer2;
	}

	public void extracteFeaturesWithCNN(String DL_INPUT_TRAINING, String DL_OUTPUT_TRAINING)
			throws FileNotFoundException, IOException, InterruptedException {
		RecordReader trainingDataReader = new CSVRecordReader();
		trainingDataReader.initialize(new FileSplit(new File(DL_INPUT_TRAINING)));
		DataSetIterator trainingDataIter = new RecordReaderDataSetIterator(trainingDataReader, batchSize);

		/*
		 * Construct the neural network
		 */
		MultiLayerConfiguration.Builder builder = new NeuralNetConfiguration.Builder().seed(seed).iterations(iterations) // Training
																															// iterations
																															// as
																															// above
				.regularization(true).l2(0.0005)
				/**
				 * Some simple advice is to start by trying three different learning rates –
				 * 1e-1, 1e-3, and 1e-6
				 */
				.learningRate(.0001)
				/**
				 * XAVIER weight initialization is usually a good choice for this. For networks
				 * with rectified linear (relu) or leaky relu activations, RELU weight
				 * initialization is a sensible choice.
				 */
				.weightInit(WeightInit.XAVIER).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
				.updater(Updater.NESTEROVS).momentum(0.9).list()
				.layer(0, new ConvolutionLayer.Builder(2, sizeOfTokenVec)
						// nIn and nOut specify depth. nIn here is the nChannels and nOut is the number
						// of filters to be applied
						.nIn(nChannels).stride(1, sizeOfTokenVec).nOut(numOfOutOfLayer1).activation(Activation.RELU)
						.build())
				.layer(1,
						new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 1).stride(2, 1)
								.build())
				.layer(2,
						new ConvolutionLayer.Builder(2, 1).stride(1, 1).nOut(numOfOutOfLayer2)
								.activation(Activation.RELU).build())
				.layer(3,
						new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2, 1).stride(2, 1)
								.build())
				.layer(4, new DenseLayer.Builder().activation(Activation.RELU).nOut(sizeOfFeatureVector).build())
				.layer(5,
						new OutputLayer.Builder(LossFunctions.LossFunction.MEAN_ABSOLUTE_ERROR).nOut(outputNum)
								.activation(Activation.SOFTMAX).build())
				.setInputType(InputType.convolutionalFlat(sizeOfVector, sizeOfTokenVec, 1)).backprop(true)
				.pretrain(false);

		MultiLayerConfiguration conf = builder.build();
		MultiLayerNetwork model = new MultiLayerNetwork(conf);
		model.init();

		model.setListeners(new ScoreIterationListener(1));

		//StringBuilder features = new StringBuilder();
		for (int i = 0; i < nEpochs; i++) {
			int batchers = 0;
			while (trainingDataIter.hasNext()) {
				try {
					DataSet next = trainingDataIter.next();
					// During the process of fitting, each training instance is used to calibrate
					// the parameters of neural network.
					model.fit(next);
					//INDArray input = model.getOutputLayer().input();
					//features.append(input.toString().replace("[[", "").replaceAll("\\],", "").replaceAll(" \\[", "")
					//		.replace("]]", "") + "\n");
				} catch(Exception e) {
					System.out.println("deu pau ");
				}
				
				batchers++;
				System.out.println("batch: " + batchers);
			}
			//features.setLength(0);
			System.out.println("*** Completed epoch {} ***" + i);

			trainingDataIter.reset();
		}

		// Save the model
		File locationToSave = new File(DL_OUTPUT_TRAINING); // Where to save the network. Note: the file is in .zip
															// format - can
		// be opened externally
		boolean saveUpdater = true; // Updater: i.e., the state for Momentum, RMSProp, Adagrad etc. Save this if you
									// want to train your network more in the future
		ModelSerializer.writeModel(model, locationToSave, saveUpdater);
	}

	public void extracteFeaturesWithCNNByLoadingModel(String DL_OUTPUT_TRAINING, String DL_INPUT_TESTING,
			String DL_OUTPUT_TESTING) throws IOException, InterruptedException {
		RecordReader testingDataReader = new CSVRecordReader();
		System.out.println("Testing" + DL_INPUT_TESTING);
		File f = new File(DL_INPUT_TESTING);
		testingDataReader.initialize(new FileSplit(new File("/home/rodrigo/DLinput_testing.csv")));
		DataSetIterator testingDataIter = new RecordReaderDataSetIterator(testingDataReader, batchSize);

		MultiLayerNetwork restored = ModelSerializer.restoreMultiLayerNetwork(DL_OUTPUT_TRAINING);
		MultiLayerNetwork model = restored.clone();

		StringBuilder featuresOfTestingData = new StringBuilder();

		int i = 0;
		while (testingDataIter.hasNext()) {
			DataSet ds = testingDataIter.next();
			List<INDArray> outputs = model.feedForward(ds.getFeatureMatrix(), false);
			INDArray testingFeatures = outputs.get(5);

			featuresOfTestingData.append(testingFeatures.toString().replace("[[", "").replaceAll("\\],", "")
					.replaceAll(" \\[", "").replace("]]", "").replaceAll("\\[", "").replaceAll("\\]", "") + "\n");
			System.out.println("Dados " + i);
			System.out.println("saida " + i);
			i++;
		}

		FileHelper.outputToFile(DL_OUTPUT_TESTING, featuresOfTestingData, false);

	}

	public MultiLayerNetwork getModel(String DL_OUTPUT_TRAINING) throws IOException, InterruptedException {
		MultiLayerNetwork restored = ModelSerializer.restoreMultiLayerNetwork(DL_OUTPUT_TRAINING);
		return restored.clone();
	}

	public double[] getOutput(MultiLayerNetwork model, double[] input) {
		INDArray id = Nd4j.create(input);
		List<INDArray> outputs = model.feedForward(id, false);
		INDArray testingFeatures = outputs.get(5);
		DataBuffer buff = testingFeatures.data();
		return buff.asDouble();
	}

}
