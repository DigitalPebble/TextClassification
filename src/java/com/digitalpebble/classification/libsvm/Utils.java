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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Lexicon;
import com.digitalpebble.classification.TrainingCorpus;
import com.digitalpebble.classification.Vector;

public class Utils {
	/**
	 * Reads the output of the reader and delivers it at string.
	 */
	public static String readOutput(BufferedReader in) throws IOException {
		StringBuffer output = new StringBuffer();
		String line = "";
		while ((line = in.readLine()) != null) {
			output.append(line);
			output.append("\n");
		}
		return output.toString();
	}

	public static File writeExamples(Document[] documents, Lexicon lexicon,
			boolean create, String vector_location) throws IOException {
		File vectorFile = new File(vector_location);
		PrintWriter out = null;
		out = new PrintWriter(new FileWriter(vectorFile));
		for (int i = 0; i < documents.length; i++) {
			Document doc = documents[i];
			int label = doc.getLabel();
			// get a vector from the document
			// need a metric (e.g. relative frequency / binary)
			// and a lexicon
			// the vector is represented as a string directly
			Vector vector = doc.getFeatureVector(lexicon);
			out.print(label + " " + Utils.getVectorString(vector) + "\n");
		}
		out.close();
		return vectorFile;
	}

	/**
	 * returns a vector i.e sequence of feature:value needs to have a pointer to
	 * the Lexicon used during the creation of the document here we generate the
	 * scores, taking into account the data in the lexicon
	 */
	private static String getVectorString(Vector vector) {
		StringBuffer buffer = new StringBuffer();
		int[] indices = vector.getIndices();
		double[] values = vector.getValues();
		for (int i = 0; i < indices.length; i++) {
			buffer.append(" ").append(indices[i]).append(":").append(values[i]);
		}
		return buffer.toString();
	}

	public static File writeExamples(TrainingCorpus corpus, Lexicon lexicon,
			boolean b, String vector_location) throws IOException {
		File vectorFile = new File(vector_location);
		PrintWriter out = null;
		out = new PrintWriter(new FileWriter(vectorFile));
		// get an iterator on the Corpus
		// and retrieve the documents one by one 
		Iterator<Document> docIterator = corpus.iterator();
		while (docIterator.hasNext()){
			Document doc = docIterator.next();
			int label = doc.getLabel();
			// get a vector from the document
			// need a metric (e.g. relative frequency / binary)
			// and a lexicon
			// the vector is represented as a string directly
			Vector vector = doc.getFeatureVector(lexicon);
			out.print(label + " " + Utils.getVectorString(vector) + "\n");
		}
		out.close();
		return vectorFile;
	}

}
