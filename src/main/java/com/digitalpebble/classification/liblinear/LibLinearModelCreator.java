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

package com.digitalpebble.classification.liblinear;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Learner;
import com.digitalpebble.classification.Lexicon;
import com.digitalpebble.classification.TrainingCorpus;
import com.digitalpebble.classification.libsvm.Utils;

import de.bwaldvogel.liblinear.Train;

public class LibLinearModelCreator extends Learner {

	private String learner_filename;

	protected String SVM_Model_location;

	protected String vector_location;

	private String outputLearner;

	public LibLinearModelCreator(String lexicon_location,
			String model_location, String vector_location) {
		lexicon = new Lexicon();
		this.SVM_Model_location = model_location;
		this.lexiconLocation = lexicon_location;
		this.vector_location = vector_location;

		learner_filename = System.getProperty("liblinear_train");
	}

	/** Returns the output generated by the SVM learner* */
	public String getOutputLearner() {
		return this.outputLearner;
	}

	protected void internal_generateVector(TrainingCorpus corpus)
			throws Exception {
		// dumps a file with the vectors for the documents
		Utils.writeExamples(corpus, this.lexicon, true, this.vector_location);
	}

	// @deprecated
	protected void internal_generateVector(Document[] documents)
			throws Exception {
		// check that we really need to do that twice
		Utils.writeExamples(documents, this.lexicon, true, vector_location);
	}

	public void internal_learn() throws Exception {
		// dumps a file with the vectors for the documents
		File learningFile = new File(this.vector_location);

		// calls the classifier
		List<String> commandList = new ArrayList<>();
		File modelFile = new File(this.SVM_Model_location);
		Process process = null;
		if (learner_filename != null)
			commandList.add(this.learner_filename);
		if (this.getParameters() != null) {
			String[] parameters = this.getParameters().split(" ");
			for (int par = 0; par < parameters.length; par++) {
				commandList.add(parameters[par]);
			}
		}
		commandList.add(learningFile.getAbsolutePath());
		commandList.add(modelFile.getAbsolutePath());
		// build the command array to pass to exec()
		String[] commandArray = (String[]) commandList
				.toArray(new String[commandList.size()]);
		if (learner_filename == null) {
			Train.main(commandArray);
		} else {
			process = Runtime.getRuntime().exec(commandArray);
			// Read output:
			try (BufferedReader in = new BufferedReader(new InputStreamReader(process
					.getInputStream()))) {
				this.outputLearner = Utils.readOutput(in);
			}
			int value = process.waitFor();
			if (value != 0)
				throw new IOException("Process unsuccessful");
		}
	}

	protected boolean supportsMultiLabels() {
		return true;
	}

	protected String getClassifierType() {
		return "com.digitalpebble.classification.liblinear.LibLinearApplier";
	}

}
