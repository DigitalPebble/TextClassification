package com.digitalpebble.classification.liblinear;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Parameters;
import com.digitalpebble.classification.TextClassifier;
import com.digitalpebble.classification.Vector;

import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;

public class LibLinearClassifier extends TextClassifier {

	Model liblinearModel;

	public double[] classify(Document document) throws Exception {

		int nr_feature = liblinearModel.getNrFeature();

		// convert docs into liblinear format

		List<FeatureNode> x = new ArrayList<FeatureNode>();

		Vector vector = document.getFeatureVector(this.lexicon);

		int[] indices = vector.getIndices();
		double[] values = vector.getValues();

		for (int indexpos = 0; indexpos < indices.length; indexpos++) {
			int index = indices[indexpos];
			if (index <= nr_feature) {
				FeatureNode node = new FeatureNode(index, values[indexpos]);
				x.add(node);
			}
		}

		if (liblinearModel.getBias() >= 0) {
			FeatureNode node = new FeatureNode(nr_feature + 1,
					liblinearModel.getBias());
			x.add(node);
		}

		FeatureNode[] nodes = new FeatureNode[x.size()];
		nodes = x.toArray(nodes);

		if (liblinearModel.isProbabilityModel()) {
			double[] prob_estimates = new double[liblinearModel.getNrClass()];
			Linear.predictProbability(liblinearModel, nodes, prob_estimates);
			return prob_estimates;
		}

		double[] dec_values = new double[liblinearModel.getNrClass()];
		Linear.predictValues(liblinearModel, nodes, dec_values);
		return dec_values;
	}

	protected void loadModel() throws Exception {
		String modelPath = pathResourceDirectory + java.io.File.separator
				+ Parameters.modelName;
		liblinearModel = Model.load(new File(modelPath));
	}

}
