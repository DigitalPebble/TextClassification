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

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.digitalpebble.classification.Parameters.WeightingMethod;

public class MultiFieldDocument implements Document {
	int label = 0;

	int[] indices;

	int[] freqs;

	// keep a link between a field index and its field num
	int[] indexToField;

	double[] tokensPerField;
	
	private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

	private MultiFieldDocument() {
	}

	/***************************************************************************
	 * A document is built from an array of Fields, with a reference to a
	 * lexicon
	 **************************************************************************/
	MultiFieldDocument(Field[] fields, Lexicon lexicon, boolean create) {

		// missing a know field?
		int maxFieldLength = Math
				.max(lexicon.getFields().length, fields.length);

		tokensPerField = new double[maxFieldLength];

		// create a vector for this document
		// from the individual tokens
		TreeMap<TokenField, int[]> tokens = new TreeMap<TokenField, int[]>();
		for (Field f : fields) {

			// get the field num from the lexicon
			final int fieldNum = lexicon.getFieldID(f._name, create);

			// field does not exist
			if (fieldNum == -1)
				continue;

			for (int token = 0; token < f._tokens.length; token++) {
				// remove null strings or empty strings
				if (f._tokens[token] == null)
					continue;
				if (f._tokens[token].length() < 1)
					continue;

				String normToken = simpleNormalisationTokenString(f._tokens[token]);

				// add a new instance to the count
				tokensPerField[fieldNum]++;

				String label = f._name + "_" + normToken;
				TokenField tf = new TokenField(label, fieldNum);
				int[] count = (int[]) tokens.get(tf);
				if (count == null) {
					count = new int[] { 0 };
					tokens.put(tf, count);
				}
				count[0]++;
			}
		}
		indices = new int[tokens.size()];
		freqs = new int[tokens.size()];
		indexToField = new int[tokens.size()];

		int lastused = 0;
		// iterates on the internal vector
		Iterator<Entry<TokenField, int[]>> iter = tokens.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<TokenField, int[]> entry = iter.next();
			TokenField key = entry.getKey();
			int[] localFreq = entry.getValue();

			// gets the index from the lexicon
			int index = -1;
			if (create) {
				index = lexicon.createIndex(key.value);
			} else {
				index = lexicon.getIndex(key.value);
			}
			// if not found in the lexicon
			// we'll just put a conventional value
			// which will help filtering it later
			if (index == -1) {
				index = Integer.MAX_VALUE;
			}
			// add it to the list
			indices[lastused] = index;
			freqs[lastused] = localFreq[0];
			indexToField[lastused] = key.field;
			lastused++;
		}
		// at this stage all the tokens are linked
		// to their indices in the lexicon
		// and we have their raw frequency in the document
		// sort the content of the vector
		quicksort(indices, freqs, indexToField, 0, indices.length - 1);
	}

	/**
	 * Returns the label of the document. The String value of the label can be
	 * accessed via the Lexicon object.*
	 */
	public int getLabel() {
		return label;
	}

	// the label is now set by the lexicon
	// and not directly by the user code
	void setLabel(int lab) {
		label = lab;
	}

	public String getStringSerialization() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(this.getClass().getSimpleName()).append("\t");
		buffer.append(this.label);
		buffer.append("\t").append(tokensPerField.length);
		for (double tokperf : tokensPerField) {
			buffer.append("\t").append(tokperf);
		}
		for (int i = 0; i < indices.length; i++) {
			buffer.append("\t").append(indices[i]).append(":").append(freqs[i])
					.append(":").append(this.indexToField[i]);
		}
		buffer.append("\n");
		return buffer.toString();
	}

	// get a String representation of the document
	// but limiting it to a subset of its fields
	public String getStringSerialization(int[] fieldToKeep) {
		if (fieldToKeep == null || fieldToKeep.length == 0)
			return getStringSerialization();
		StringBuffer buffer = new StringBuffer();
		buffer.append(this.getClass().getSimpleName()).append("\t");
		buffer.append(this.label);
		buffer.append("\t").append(tokensPerField.length);
		for (int fieldNum = 0; fieldNum < tokensPerField.length; fieldNum++) {
			double tokperf = tokensPerField[fieldNum];
			if (java.util.Arrays.binarySearch(fieldToKeep, fieldNum) != -1)
				buffer.append("\t").append(tokperf);
			else
				buffer.append("\t").append("0.0");
		}
		for (int i = 0; i < indices.length; i++) {
			int fieldNum = this.indexToField[i];
			if (java.util.Arrays.binarySearch(fieldToKeep, fieldNum) != -1)
				buffer.append("\t").append(indices[i]).append(":").append(
						freqs[i]).append(":").append(this.indexToField[i]);
		}
		buffer.append("\n");
		return buffer.toString();
	}

	public static Document parse(String line) {
		String[] splits = line.split("\t");
		if (splits.length < 4)
			return null;
		// ignore first part
		MultiFieldDocument newdoc = new MultiFieldDocument();
		try {
			newdoc.label = Integer.parseInt(splits[1]);
			int numFields = Integer.parseInt(splits[2]);

			newdoc.tokensPerField = new double[numFields];

			int currentPos = 3;
			for (int i = 0; i < numFields; i++) {
				String sizeField = splits[currentPos];
				newdoc.tokensPerField[i] = Double.parseDouble(sizeField);
				currentPos++;
			}

			// num features
			int numfeatures = splits.length - currentPos;
			newdoc.freqs = new int[numfeatures];
			newdoc.indices = new int[numfeatures];
			newdoc.indexToField = new int[numfeatures];

			int lastPos = 0;
			for (; currentPos < splits.length; currentPos++) {
				// x:y:z
				String[] subsplits = splits[currentPos].split(":");
				newdoc.indices[lastPos] = Integer.parseInt(subsplits[0]);
				newdoc.freqs[lastPos] = Integer.parseInt(subsplits[1]);
				newdoc.indexToField[lastPos] = Integer.parseInt(subsplits[2]);
				lastPos++;
			}
		} catch (Exception e) {
			return null;
		}
		return newdoc;
	}

	/**
	 * Returns a Vector representation of the document. This Vector object is
	 * weighted and used by the instances of Learner or TextClassifier
	 */
	public Vector getFeatureVector(Lexicon lexicon) {
		Parameters.WeightingMethod method = lexicon.getMethod();
		return getFeatureVector(lexicon, method, null);
	}

	public Vector getFeatureVector(Lexicon lexicon,
			Parameters.WeightingMethod method) {
		return getFeatureVector(lexicon, method, null);
	}

	public Vector getFeatureVector(Lexicon lexicon, Map<Integer, Integer> equiv) {
		Parameters.WeightingMethod method = lexicon.getMethod();
		return getFeatureVector(lexicon, method, equiv);
	}

	public Vector getFeatureVector(Lexicon lexicon,
			Parameters.WeightingMethod method, Map<Integer, Integer> equiv) {
		// we need to iterate on the features
		// of this document and compute a score
		double numDocs = (double) lexicon.getDocNum();

		// have the attribute numbers been changed in
		// the meantime?
		if (equiv != null) {
			for (int pos = 0; pos < indices.length; pos++) {
				Integer newPos = equiv.get(indices[pos]);
				// filtered
				if (newPos == null)
					indices[pos] = Integer.MAX_VALUE;
				else
					indices[pos] = newPos.intValue();
			}
			// resort the indices
			quicksort(indices, freqs, indexToField, 0, indices.length - 1);
		}

		int kept = 0;
		double[] copyvalues = new double[indices.length];
		for (int pos = 0; pos < indices.length; pos++) {
			// need to check that a given term has not
			// been filtered since the creation of the corpus
			// the indices are sorted so we know there is no point
			// in going further
			// Integer.MAX_VALUE == unknown in model
			if (indices[pos] == Integer.MAX_VALUE) {
				break;
			}
			if (lexicon.getDocFreq(indices[pos]) <= 0)
				continue;
			double score = getScore(pos, lexicon, numDocs);
			// removed in meantime?
			if (score == 0)
				continue;
			copyvalues[pos] = score;
			kept++;
		}
		// trim to size
		int[] trimmedindices = new int[kept];
		double[] trimmedvalues = new double[kept];

		// normalize the values?
		if (lexicon.isNormalizeVector())
			normalizeL2(trimmedvalues);

		System.arraycopy(indices, 0, trimmedindices, 0, kept);
		System.arraycopy(copyvalues, 0, trimmedvalues, 0, kept);
		return new Vector(trimmedindices, trimmedvalues);
	}

	/**
	 * Returns the score of an attribute given the weighting scheme specified in
	 * the lexicon or for a specific field
	 **/
	private double getScore(int pos, Lexicon lexicon, double numdocs) {
		double score = 0;
		int indexTerm = this.indices[pos];
		double occurences = (double) this.freqs[pos];

		int fieldNum = this.indexToField[pos];
		double frequency = occurences / tokensPerField[fieldNum];

		// is there a custom weight for this field?
		String fieldName = lexicon.getFields()[fieldNum];
		WeightingMethod method = lexicon.getMethod(fieldName);

		if (method.equals(Parameters.WeightingMethod.BOOLEAN)) {
			score = 1;
		} else if (method.equals(Parameters.WeightingMethod.OCCURRENCES)) {
			score = occurences;
		} else if (method.equals(Parameters.WeightingMethod.FREQUENCY)) {
			score = frequency;
		} else if (method.equals(Parameters.WeightingMethod.TFIDF)) {
			int df = lexicon.getDocFreq(indexTerm);
			double idf = numdocs / (double) df;
			score = frequency * Math.log(idf);
			if (idf == 1)
				score = frequency;
		}
		return score;
	}

	/**
	 * Returns the L2 norm factor of this vector's values.
	 */
	private void normalizeL2(double[] scores) {
		double square_sum = 0.0;
		for (int i = 0; i < scores.length; i++) {
			square_sum += (scores[i] * scores[i]);
		}
		double norm = Math.sqrt(square_sum);
		if (norm != 0)
			for (int i = 0; i < scores.length; i++) {
				scores[i] = scores[i] / norm;
			}
	}

	private int partition(int[] dims, int[] vals, int[] vals2, int low, int high) {
		double pivotprim = 0;
		int i = low - 1;
		int j = high + 1;
		pivotprim = dims[(low + high) / 2];
		while (i < j) {
			i++;
			while (dims[i] < pivotprim)
				i++;
			j--;
			while (dims[j] > pivotprim)
				j--;
			if (i < j) {
				int tmp = dims[i];
				dims[i] = dims[j];
				dims[j] = tmp;
				int tmpd = vals[i];
				vals[i] = vals[j];
				vals[j] = tmpd;
				int t2mpd = vals2[i];
				vals2[i] = vals2[j];
				vals2[j] = t2mpd;
			}
		}
		return j;
	}

	private void quicksort(int[] dims, int[] vals, int[] vals2, int low,
			int high) {
		if (low >= high)
			return;
		int p = partition(dims, vals, vals2, low, high);
		quicksort(dims, vals, vals2, low, p);
		quicksort(dims, vals, vals2, p + 1, high);
	}

	class TokenField implements Comparable<TokenField> {
		int field;

		String value;

		TokenField(String val, int fieldNum) {
			field = fieldNum;
			value = val;
		}

		public int compareTo(TokenField tf) {
			return value.compareTo(tf.value);
		}

	}

	/**
	 * this is done to make sure that the lexicon file will be read properly and
	 * won't contain any characters that would break it
	 **/
	private static String simpleNormalisationTokenString(String token) {
	    return SPACE_PATTERN.matcher(token).replaceAll("_");
	}

}