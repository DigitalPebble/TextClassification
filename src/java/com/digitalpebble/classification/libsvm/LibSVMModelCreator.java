/**
 * Copyright 2009 DigitalPebble Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.digitalpebble.classification.libsvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;
import libsvm.svm_problem_impl;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Learner;
import com.digitalpebble.classification.Lexicon;
import com.digitalpebble.classification.TrainingCorpus;

public class LibSVMModelCreator extends Learner {
	private svm_parameter param; // set by parse_command_line

	private svm_problem prob; // set by read_problem

	private svm_model model;

	private String model_file_name;

	protected String vector_location;

	private String error_msg;

	private int nfold = 0;

	private boolean cross_validation = false;

	public LibSVMModelCreator(String lexicon_location, String model_location,
			String vectorFile) {
		lexicon = new Lexicon();
		this.model_file_name = model_location;
		this.lexiconLocation = lexicon_location;
		this.vector_location = vectorFile;
	}

	protected void internal_generateVector(TrainingCorpus corpus)
			throws Exception {
		// dumps a file with the vectors for the documents
		Utils.writeExamples(corpus, this.lexicon, true,this.vector_location);
	}

	protected void internal_generateVector(Document[] documents)
			throws Exception {
		// dumps a file with the vectors for the documents
		Utils
				.writeExamples(documents, this.lexicon, true,
						this.vector_location);
	}

	public void internal_learn() throws Exception {
		// dumps a file with the vectors for the documents
		File learningFile = new File(this.vector_location);

		// make space
		parse_command_line();
		if (cross_validation && nfold < 2)
			throw new Exception("n-fold cross validation: n must >= 2\n");
		read_problem(learningFile);
		error_msg = svm.svm_check_parameter(prob, param);
		if (error_msg != null) {
			System.err.print("Error: " + error_msg + "\n");
			throw new Exception(error_msg);
		}
		if (cross_validation) {
			do_cross_validation();
		} else {
			model = svm.svm_train(prob, param);
			svm.svm_save_model(model_file_name, model);
			// dump linear weights in lexicon
			try {
				double[] weights = model.getLinearWeights();
				this.lexicon.setLinearWeight(weights);
			} catch (Exception e) {
			}
		}
	}

	private void parse_command_line() {
		int i;
		String[] argv = new String[0];
		if (getParameters() != null)
			argv = getParameters().split(" ");
		param = new svm_parameter();
		// default values
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.RBF;
		param.degree = 3;
		param.gamma = 0; // 1/k
		param.coef0 = 0;
		param.nu = 0.5;
		param.cache_size = 100;
		param.C = 1;
		param.eps = 1e-3;
		param.p = 0.1;
		param.shrinking = 1;
		param.probability = 0;
		param.nr_weight = 0;
		param.weight_label = new int[0];
		param.weight = new double[0];
		this.cross_validation = false;
		this.nfold = 0;
		// parse options
		for (i = 0; i < argv.length; i++) {
			if (argv[i].charAt(0) != '-')
				break;
			if (++i >= argv.length)
				return;
			switch (argv[i - 1].charAt(1)) {
			case 's':
				param.svm_type = Integer.parseInt(argv[i]);
				break;
			case 't':
				param.kernel_type = Integer.parseInt(argv[i]);
				break;
			case 'd':
				param.degree = Integer.parseInt(argv[i]);
				break;
			case 'g':
				param.gamma = Float.parseFloat(argv[i]);
				break;
			case 'r':
				param.coef0 = Integer.parseInt(argv[i]);
				break;
			case 'n':
				param.nu = Integer.parseInt(argv[i]);
				break;
			case 'm':
				param.cache_size = Integer.parseInt(argv[i]);
				break;
			case 'c':
				param.C = Float.parseFloat(argv[i]);
				break;
			case 'e':
				param.eps = Integer.parseInt(argv[i]);
				break;
			case 'p':
				param.p = Integer.parseInt(argv[i]);
				break;
			case 'v':
				nfold = Integer.parseInt(argv[i]);
				cross_validation = true;
				break;
			case 'h':
				param.shrinking = Integer.parseInt(argv[i]);
				break;
			case 'b':
				param.probability = Integer.parseInt(argv[i]);
				break;
			case 'w':
				++param.nr_weight;
				{
					int[] old = param.weight_label;
					param.weight_label = new int[param.nr_weight];
					System.arraycopy(old, 0, param.weight_label, 0,
							param.nr_weight - 1);
				}
				{
					double[] old = param.weight;
					param.weight = new double[param.nr_weight];
					System.arraycopy(old, 0, param.weight, 0,
							param.nr_weight - 1);
				}
				param.weight_label[param.nr_weight - 1] = Integer
						.parseInt(argv[i - 1].substring(2));
				param.weight[param.nr_weight - 1] = Integer.parseInt(argv[i]);
				break;
			default:
				System.err.print("unknown option\n");
			}
		}
	}

	// read in a problem (in svmlight format)
	private void read_problem(File learningFile) throws IOException {
		BufferedReader fp = new BufferedReader(new FileReader(learningFile));
		Vector vy = new Vector();
		Vector vx = new Vector();
		int max_index = 0;
		while (true) {
			String line = fp.readLine();
			if (line == null)
				break;
			StringTokenizer st = new StringTokenizer(line, " \t\n\r\f:");
			vy.addElement(st.nextToken());
			int m = st.countTokens() / 2;
			svm_node[] x = new svm_node[m];
			for (int j = 0; j < m; j++) {
				x[j] = new svm_node();
				x[j].index = Integer.parseInt(st.nextToken());
				x[j].value = Double.parseDouble(st.nextToken());
			}
			if (m > 0)
				max_index = Math.max(max_index, x[m - 1].index);
			vx.addElement(x);
		}
		prob = new svm_problem_impl(vy.size());
		for (int i = 0; i < prob.size(); i++)
			prob.setNodes(i, (svm_node[]) vx.elementAt(i));
		for (int i = 0; i < prob.size(); i++) {
			double labell = Double.parseDouble((String) vy.elementAt(i));
			prob.setLabel(i, labell);
		}
		if (param.gamma == 0)
			param.gamma = 1.0 / max_index;
		fp.close();
	}

	protected boolean supportsMultiLabels() {
		return true;
	}

	protected String getClassifierType() {
		return "com.digitalpebble.classification.libsvm.LibSVMClassifier";
	}

	private void do_cross_validation() {
		int i;
		int total_correct = 0;
		double total_error = 0;
		double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;
		double size = prob.size();
		double[] target = new double[prob.size()];
		svm.svm_cross_validation(prob, param, this.nfold, target);
		if (param.svm_type == svm_parameter.EPSILON_SVR
				|| param.svm_type == svm_parameter.NU_SVR) {
			for (i = 0; i < prob.size(); i++) {
				double y = prob.getLabel(i);
				double v = target[i];
				total_error += (v - y) * (v - y);
				sumv += v;
				sumy += y;
				sumvv += v * v;
				sumyy += y * y;
				sumvy += v * y;
			}
			System.out.print("Cross Validation Mean squared error = "
					+ total_error / size + "\n");
			System.out
					.print("Cross Validation Squared correlation coefficient = "
							+ ((size * sumvy - sumv * sumy) * (size * sumvy - sumv
									* sumy))
							/ ((size * sumvv - sumv * sumv) * (size * sumyy - sumy
									* sumy)) + "\n");
			return;
		}
		int numclasses = lexicon.getLabelNum();
		double[][] confMatrix = new double[numclasses][numclasses];
		for (i = 0; i < size; i++) {
			double expected = prob.getLabel(i);
			if (target[i] == expected)
				++total_correct;
			confMatrix[(int) target[i]][(int) expected]++;
		}
		System.out.println("\n**************");

		double[] totalFoundLabel = new double[confMatrix.length];
		double[] totalExpectedLabel = new double[confMatrix.length];

		// display the confusion matrix?
		for (i = 0; i < confMatrix.length; i++) {
			StringBuffer line = new StringBuffer();
			line.append(lexicon.getLabel(i));
			for (int j = 0; j < confMatrix.length; j++) {
				line.append("\t");
				int confMatrixint = (int) confMatrix[i][j];
				totalFoundLabel[j] += confMatrix[i][j];
				totalExpectedLabel[i] += confMatrix[i][j];
				line.append(confMatrixint);
			}
			System.out.println(line.toString());
		}
		double overallAcc = (double) (total_correct) / (double) size;
		System.out.print("\nCross Validation Accuracy = "
				+ accuracyPrettyPrinter(overallAcc) + "%\n\n");

		// find the number of hits
		// and display the Precision per label
		for (i = 0; i < confMatrix.length; i++) {
			StringBuffer line = new StringBuffer();
			line.append(lexicon.getLabel(i)).append(" ");
			double precision = 0;
			double recall = 0;

			if (totalFoundLabel[i] != 0)
				precision = confMatrix[i][i] / totalFoundLabel[i];
			line.append(" precision: ");
			line.append(accuracyPrettyPrinter(precision));

			if (totalExpectedLabel[i] != 0)
				recall = confMatrix[i][i] / totalExpectedLabel[i];
			line.append(" recall: ");
			line.append(accuracyPrettyPrinter(recall));

			System.out.println(line.toString());
		}

		System.out.println("\n\nMisclassified:\n\n");

		// display the misclassified examples
		Map<Integer, String> inverted = lexicon.getInvertedIndex();
		for (i = 0; i < size; i++) {
			StringBuffer sb = new StringBuffer();
			double expected = prob.getLabel(i);
			if (target[i] == expected)
				continue;
			sb.append("expected: ").append(lexicon.getLabel((int) expected))
					.append("\tfound:").append(
							lexicon.getLabel((int) target[i]));
			svm_node[] nodes = prob.getNodes(i);
			for (svm_node node : nodes) {
				String attLabel = inverted.get(new Integer(node.index));
				if (attLabel == null)
					attLabel = "null:" + node.index;
				sb.append("\t").append(attLabel);
			}
			System.out.println(sb.toString());
		}

	}

	private String accuracyPrettyPrinter(double d) {
		String score = Double.toString(d);
		if (score.length() > 5)
			score = score.substring(0, 5);
		return score;
	}

}
