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

package com.digitalpebble.classification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.digitalpebble.classification.Parameters.WeightingMethod;
import com.digitalpebble.classification.util.AttributeScorer;

/**
 * A lexicon contains all the information about the tokens used during learning
 * and ensures that the same mapping is used during classification
 */
public class Lexicon {

	private TreeMap<String, int[]> tokenForm2index;

	private TreeMap<Integer, int[]> index2docfreq;

	private int nextAttributeID = 1;

	// private int method_used = Parameters.method_frequency;
	private Parameters.WeightingMethod method_used = Parameters.WeightingMethod.FREQUENCY;

	private int docNum = 0;

	private boolean normalizeVector = true;

	private double[] linearWeight;

	// private double[] loglikelihoodratio;

	private List<String> labels;

	/** a learner can specify which classifier to use* */
	private String classifierType;

	/** list of fields used by a corpus * */
	private Map<String, Integer> fields = new HashMap<String, Integer>();

	/** Custom weighting schemes for fields **/
	private Map<String, WeightingMethod> customWeights = new HashMap<String, WeightingMethod>();

	private int lastFieldId = -1;

	private AttributeScorer filter;

	// creates a new lexicon
	public Lexicon() {
		tokenForm2index = new TreeMap<String, int[]>();
		index2docfreq = new TreeMap();
		labels = new ArrayList<String>();
	}

	// loads a new lexicon
	public Lexicon(String file) throws IOException {
		this();
		this.loadFromFile(file);
	}

	/**
	 * Returns the weighting scheme used for a specific field or the default one
	 * if nothing has been specified for it
	 **/
	public WeightingMethod getMethod(String fieldName) {
		WeightingMethod method = this.customWeights.get(fieldName);
		if (method != null)
			return method;
		return this.method_used;
	}

	/** Returns the default weighting scheme **/
	public WeightingMethod getMethod() {
		return this.method_used;
	}

	/** Sets the default weighting scheme **/
	public void setMethod(WeightingMethod method) {
		this.method_used = method;
	}

	/** Sets the weighting scheme for a specific field **/
	public void setMethod(WeightingMethod method, String fieldName) {
		WeightingMethod existingmethod = this.customWeights.get(fieldName);
		if (existingmethod == null) {
			this.customWeights.put(fieldName, method);
			return;
		}
		// already one specified : check that it is the same as the one we have
		if (!method.equals(existingmethod))
			throw new RuntimeException("Already set weight of field "
					+ fieldName + " to " + existingmethod.toString());
	}

	public int getDocNum() {
		return this.docNum;
	}

	public int getLabelNum() {
		return this.labels.size();
	}

	public Integer getFieldID(String fieldName, boolean create) {
		Integer id = fields.get(fieldName);
		if (id == null) {
			// field does not exist
			if (!create)
				return new Integer(-1);
			fields.put(fieldName, ++lastFieldId);
			return new Integer(lastFieldId);
		}
		return id;
	}

	public String[] getFields() {
		String[] ff = new String[fields.size()];
		Iterator iter = fields.keySet().iterator();
		while (iter.hasNext()) {
			String fname = (String) iter.next();
			Integer integ = fields.get(fname);
			ff[integ.intValue()] = fname;
		}
		return ff;
	}

	public String[] getLabels() {
		String[] labs = new String[labels.size()];
		for (int l = 0; l < labels.size(); l++)
			labs[l] = (String) labels.get(l);
		return labs;
	}

	public void incrementDocCount() {
		this.docNum++;
	}

	/**
	 * returns the position of a given tokenform or -1 if the tokenform is
	 * unknown or has been filtered out
	 * 
	 * @param tokenForm
	 * @return
	 */
	public int getIndex(String tokenForm) {
		tokenForm = tokenForm.replaceAll("\\W+", "_");
		int[] index = (int[]) tokenForm2index.get(tokenForm);
		if (index == null)
			return -1;
		return index[0];
	}

	/***************************************************************************
	 * Returns the document frequency of a term in the collection or 0 if the
	 * term is unknown or has been filtered
	 **************************************************************************/
	public int getDocFreq(int term) {
		int[] docfreq = (int[]) this.index2docfreq.get(new Integer(term));
		if (docfreq == null)
			return 0;
		return docfreq[0];
	}

	public void pruneTermsDocFreq(int mindn, int maxdocs) {
		// iterate on the terms
		// and remove them if they are below or above
		// the expected number of documents
		Iterator termIter = this.tokenForm2index.keySet().iterator();
		List terms2remove = new ArrayList();
		while (termIter.hasNext()) {
			String term = (String) termIter.next();
			int[] index = this.tokenForm2index.get(term);
			// get the docFreq
			int[] docfreq = (int[]) this.index2docfreq
					.get(new Integer(index[0]));
			if ((docfreq[0] < mindn) || (docfreq[0] > maxdocs)) {
				// remove it!
				terms2remove.add(term);
			}
		}

		for (int i = 0; i < terms2remove.size(); i++) {
			String term = (String) terms2remove.get(i);
			int[] index = this.tokenForm2index.remove(term);
			this.index2docfreq.remove(new Integer(index[0]));
		}

	}

	/** Keep the top n attributes according to an AttributeFilter* */
	public void applyAttributeFilter(AttributeScorer filter, int rank) {
		if (filter == null)
			return;
		if (rank >= this.getAttributesNum())
			return;
		// get the threshold
		double threshold = filter.getValueForRank(rank);
		// iterate on the attributes
		// and remove them if their LLR score is below the threshold
		Iterator termIter = this.tokenForm2index.keySet().iterator();
		List terms2remove = new ArrayList();
		while (termIter.hasNext()) {
			String term = (String) termIter.next();
			int[] index = this.tokenForm2index.get(term);
			// get the score
			// TODO what if we are getting -1
			if (filter.getScore(index[0]) < threshold)
				terms2remove.add(term);
		}
		for (int i = 0; i < terms2remove.size(); i++) {
			String term = (String) terms2remove.get(i);
			int[] index = this.tokenForm2index.remove(term);
			this.index2docfreq.remove(new Integer(index[0]));
		}
	}

	// creates an entry for the token
	// called from Document
	public int createIndex(String tokenForm) {
		int[] index = (int[]) tokenForm2index.get(tokenForm);
		if (index == null) {
			index = new int[] { nextAttributeID };
			tokenForm2index.put(tokenForm, index);
			nextAttributeID++;
		}
		// add information about number of documents
		// for the term
		Integer integ = new Integer(index[0]);
		int[] docfreq = (int[]) this.index2docfreq.get(integ);
		if (docfreq == null) {
			docfreq = new int[] { 0 };
			index2docfreq.put(integ, docfreq);
		}
		docfreq[0]++;
		return index[0];
	}

	private void loadFromFile(String filename) throws IOException {
		File file = new File(filename);
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = null;
		this.docNum = Integer.parseInt(reader.readLine());
		this.method_used = Parameters.WeightingMethod.methodFromString(reader
				.readLine());
		this.normalizeVector = Boolean.parseBoolean(reader.readLine());
		this.classifierType = reader.readLine();
		this.labels = Arrays.asList(reader.readLine().split(" "));
		String[] tmp = reader.readLine().split(" ");
		for (String f : tmp) {
			// see if there is a custom weight for it
			String[] fieldTokens = f.split(":");
			String field_name = fieldTokens[0];
			if (fieldTokens.length > 1) {
				WeightingMethod method = Parameters.WeightingMethod
						.methodFromString(fieldTokens[1]);
				customWeights.put(field_name, method);
			}
			getFieldID(field_name, true);
		}
		int loaded = 0;
		int highestID = 0;
		Pattern tab = Pattern.compile("\t");
		while ((line = reader.readLine()) != null) {
			String[] content_pos = tab.split(line);
			int index = Integer.parseInt(content_pos[1]);
			if (index > highestID)
				highestID = index;
			int docs = Integer.parseInt(content_pos[2]);
			int[] aindex = new int[] { index };
			int[] adocs = new int[] { docs };
			this.tokenForm2index.put(content_pos[0], aindex);
			this.index2docfreq.put(new Integer(index), adocs);
			loaded++;
		}
		this.nextAttributeID = highestID + 1;
		reader.close();
	}

	public void saveToFile(String filename) throws IOException {
		File file = new File(filename);
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		Iterator forms = this.tokenForm2index.keySet().iterator();
		// saves the number of documents in the corpus
		writer.write(this.docNum + "\n");
		// saves the method used
		writer.write(this.method_used.toString() + "\n");
		// saves the normalization
		writer.write(this.normalizeVector + "\n");
		// saves the classifier into
		writer.write(this.classifierType + "\n");
		// saves the list of labels
		Iterator labelIters = this.labels.iterator();
		while (labelIters.hasNext()) {
			writer.write((String) labelIters.next() + " ");
		}
		writer.write("\n");
		// save the field names (possibly with non default scheme)
		for (String fname : this.getFields()) {
			writer.write(fname);
			WeightingMethod method = customWeights.get(fname);
			if (method != null)
				writer.write(":" + method.name());
			writer.write(" ");
		}
		writer.write("\n");

		// dump all token_forms one by one
		while (forms.hasNext()) {
			String key = (String) forms.next();
			int indexTerm = ((int[]) this.tokenForm2index.get(key))[0];
			int docfreq = this.getDocFreq(indexTerm);
			// dumps the weight of the term
			// or skip the term if it has a weight of 0
			String weight = "";
			if (linearWeight != null) {
				if (indexTerm >= linearWeight.length
						|| linearWeight[indexTerm] == 0)
					continue;
				weight = "\t" + linearWeight[indexTerm];
			}

			String score = "";
			// if (loglikelihoodratio != null) {
			// score = "\t" + loglikelihoodratio[indexTerm];
			// }
			if (filter != null) {
				score = "\t" + filter.getScore(indexTerm);
			}

			writer.write(key + "\t" + indexTerm + "\t" + docfreq + weight
					+ score + "\n");
		}
		writer.close();
	}

	public boolean isNormalizeVector() {
		return normalizeVector;
	}

	/**
	 * contribution of the attributes to the model used by linear models in
	 * libSVM or svmlight
	 */
	public void setLinearWeight(double[] linearWeight) {
		this.linearWeight = linearWeight;
	}

	public void setNormalizeVector(boolean normalizeVector) {
		this.normalizeVector = normalizeVector;
	}

	public int getLabelIndex(String label) {
		label = label.toLowerCase();
		label = label.replace(' ', '_');
		int position = this.labels.indexOf(label);
		if (position != -1)
			return position;
		this.labels.add(label.toLowerCase());
		return this.labels.size() - 1;
	}

	/** Return a map with Integers as keys and attribute labels as value* */
	public Map<Integer, String> getInvertedIndex() {
		TreeMap<Integer, String> inverted = new TreeMap<Integer, String>();
		Iterator<String> keyiter = this.tokenForm2index.keySet().iterator();
		while (keyiter.hasNext()) {
			String key = keyiter.next();
			int[] index = tokenForm2index.get(key);
			Integer i = new Integer(index[0]);
			inverted.put(i, key);
		}
		return inverted;
	}

	public String getLabel(int index) {
		return (String) this.labels.get(index);
	}

	protected String getClassifierType() {
		return classifierType;
	}

	protected void setClassifierType(String classifierType) {
		this.classifierType = classifierType;
	}

	public int getAttributesNum() {
		return tokenForm2index.size();
		// return nextTokenPosition;
	}

	// returns the largest ID used for an attribute
	public int maxAttributeID() {
		return nextAttributeID - 1;
	}

	public void setAttributeScorer(AttributeScorer f) {
		this.filter = f;
	}

}
