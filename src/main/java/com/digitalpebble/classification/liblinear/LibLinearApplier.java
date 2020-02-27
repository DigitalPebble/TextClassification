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
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Parameters;
import com.digitalpebble.classification.TextClassifier;
import com.digitalpebble.classification.libsvm.Utils;

public class LibLinearApplier extends TextClassifier {

	URL model;
	
	private String parameters = null;

	private String classifier_filename = "liblinear_predict";	
	
	public LibLinearApplier (){
		classifier_filename =  System.getProperty("liblinear_predict","./liblinear_predict");
	}
	
	protected void loadModel() throws Exception {
		String modelPath = pathResourceDirectory + java.io.File.separator
				+ Parameters.modelName;
		if (modelPath.startsWith("file:///") == false) {
			modelPath = "file:///" + modelPath;
		}
		model = new URL(modelPath);
	}

	public double[] classify(Document document) throws Exception {
		return classify(new Document[]{document})[0];
	}
	
	public double[][] classify(Document[] documents) throws Exception {
		double[][] predictions = new double[documents.length][lexicon
				.getLabelNum()];
		// get a vectorial representation of the documents to classify
		// call the classifier on that
		// direct the output towards a temp file
		// from which we'll read the outcomes
		File tempInputVector = File.createTempFile("LibLinearApplier_classify", "");
		File tempOutputVector= new File(tempInputVector.getParent(),tempInputVector.getName()+".predictions");
		tempOutputVector.createNewFile();
		
		Utils.writeExamples(documents, lexicon, true, tempInputVector.getAbsolutePath());
		
		internal_classify(tempInputVector,tempOutputVector,predictions);
		
		// removed the input and output files
		tempInputVector.delete();
		tempOutputVector.delete();
		
		return predictions;
	}
	
	private void internal_classify(File input, File output,double[][] predictions) throws Exception {
		// calls the classifier
		List<String> commandList = new ArrayList<String> ();
		File modelFile = new File(model.getFile());
		Process process = null;
		commandList.add(this.classifier_filename);
		if (this.getParameters() != null) {
			String[] parameters = this.getParameters().split(" ");
			for (int par = 0; par < parameters.length; par++) {
				commandList.add(parameters[par]);
			}
		}
		commandList.add(input.getAbsolutePath());
		commandList.add(modelFile.getAbsolutePath());
		commandList.add(output.getAbsolutePath());
		
		// build the command array to pass to exec()
		String[] commandArray = (String[]) commandList
				.toArray(new String[commandList.size()]);
		
		process = Runtime.getRuntime().exec(commandArray);
		
		int value = process.waitFor();
		if (value != 0)
			throw new IOException("Process unsuccessful");
		// Read labels from output file
		BufferedReader in = new BufferedReader(new FileReader(output));
		String line = null;
		int docNum = 0;
		while ((line=in.readLine())!=null){
			// line contains only the label
			int label = Integer.parseInt(line);
			// set the prediction
			predictions[docNum][label]=1.0f;
			docNum++;
		}
		in.close();
	}

	private String getParameters() {
		return parameters;
	}
	
	private void setParameters(String p) {
		parameters= p;
	}

}
