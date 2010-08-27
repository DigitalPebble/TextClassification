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
import java.util.TreeMap;

/*******************************************************************************
 * A Document is built by an instance of Learner or Classifier
 ******************************************************************************/
public class SimpleDocument implements Document {
  int label = 0;
  
  int[] indices;
  
  int[] freqs;
  
  double totalNumberTokens = 0;
  
  // used when building from serialisation
  private SimpleDocument() {};
  
  /***************************************************************************
   * A document is built from an array of Strings, with a reference to a lexicon
   **************************************************************************/
  SimpleDocument(String[] tokenstring, Lexicon lexicon, boolean create) {
    
    totalNumberTokens = 0;
    
    // create a vector for this document
    // from the individual tokens
    TreeMap tokens = new TreeMap();
    for (int token = 0; token < tokenstring.length; token++) {
      // remove null strings or empty strings
      if (tokenstring[token] == null) continue;
      if (tokenstring[token].length() < 1) continue;
      // add a new instance to the count
      totalNumberTokens++;
      String normToken = simpleNormalisationTokenString(tokenstring[token]);
      int[] count = (int[]) tokens.get(normToken);
      if (count == null) {
        count = new int[] {0};
        tokens.put(normToken, count);
      }
      count[0]++;
    }
    indices = new int[tokens.size()];
    freqs = new int[tokens.size()];
    int lastused = 0;
    // iterates on the internal vector
    Iterator iter = tokens.keySet().iterator();
    while (iter.hasNext()) {
      String key = (String) iter.next();
      int[] localFreq = (int[]) tokens.get(key);
      // gets the index from the lexicon
      int index = -1;
      if (create) {
        index = lexicon.createIndex(key);
      } else {
        index = lexicon.getIndex(key);
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
      lastused++;
    }
    // at this stage all the tokens are linked
    // to their indices in the lexicon
    // and we have their raw frequency in the document
    // sort the content of the vector
    quicksort(indices, freqs, 0, indices.length - 1);
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see com.digitalpebble.classification.DocumentInterface#getLabel()
   */
  public int getLabel() {
    return label;
  }
  
  // the label is now set by the lexicon
  // and not directly by the user code
  void setLabel(int lab) {
    label = lab;
  }
  
  public Vector getFeatureVector(Lexicon lexicon) {
    Parameters.WeightingMethod method = lexicon.getMethod();
    return getFeatureVector(lexicon, method);
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see
   * com.digitalpebble.classification.Document#getFeatureVector(com.digitalpebble
   * .classification.Lexicon)
   */
  public Vector getFeatureVector(Lexicon lexicon,
      Parameters.WeightingMethod method) {
    // we need to iterate on the features
    // of this document and compute a score
    double numDocs = (double) lexicon.getDocNum();
    int kept = 0;
    double[] copyvalues = new double[indices.length];
    for (int pos = 0; pos < indices.length; pos++) {
      // need to check that a given term has not
      // been filtered since the creation of the corpus
      // hasMaxValue == unknown in model
      if (indices[pos] == Integer.MAX_VALUE) {
        break;
      }
      if (lexicon.getDocFreq(indices[pos]) <= 0) continue;
      double score = getScore(pos, lexicon, method, numDocs);
      copyvalues[pos] = score;
      kept++;
    }
    // trim to size
    int[] trimmedindices = new int[kept];
    double[] trimmedvalues = new double[kept];
    
    // normalize the values?
    if (lexicon.isNormalizeVector()) normalizeL2(trimmedvalues);
    
    System.arraycopy(indices, 0, trimmedindices, 0, kept);
    System.arraycopy(copyvalues, 0, trimmedvalues, 0, kept);
    return new Vector(trimmedindices, trimmedvalues);
  }
  
  private double getScore(int pos, Lexicon lexicon,
      Parameters.WeightingMethod method, double numdocs) {
    double score = 0;
    int indexTerm = this.indices[pos];
    double occurences = (double) this.freqs[pos];
    double frequency = occurences / totalNumberTokens;
    
    if (method == Parameters.WeightingMethod.BOOLEAN) {
      score = 1;
    } else if (method == Parameters.WeightingMethod.OCCURRENCES) {
      score = occurences;
    } else if (method == Parameters.WeightingMethod.FREQUENCY) {
      score = frequency;
    } else if (method == Parameters.WeightingMethod.TFIDF) {
      int df = lexicon.getDocFreq(indexTerm);
      double idf = numdocs / (double) df;
      score = frequency * Math.log(idf);
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
    if (norm != 0) for (int i = 0; i < scores.length; i++) {
      scores[i] = scores[i] / norm;
    }
  }
  
  private int partition(int[] dims, int[] vals, int low, int high) {
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
      }
    }
    return j;
  }
  
  private void quicksort(int[] dims, int[] vals, int low, int high) {
    if (low >= high) return;
    int p = partition(dims, vals, low, high);
    quicksort(dims, vals, low, p);
    quicksort(dims, vals, p + 1, high);
  }
  
  public String getStringSerialization() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(this.getClass().getSimpleName()).append("\t");
    buffer.append(this.label).append("\t");
    buffer.append(this.totalNumberTokens);
    for (int i = 0; i < indices.length; i++) {
      buffer.append("\t").append(indices[i]).append(":").append(freqs[i]);
    }
    buffer.append("\n");
    return buffer.toString();
  }
  
  /** Build a document from a serialized string **/
  public static SimpleDocument parse(String line) {
    String[] splits = line.split("\t");
    if (splits.length < 4) return null;
    // ignore first part
    SimpleDocument newdoc = new SimpleDocument();
    try {
      newdoc.label = Integer.parseInt(splits[1]);
      newdoc.totalNumberTokens = Integer.parseInt(splits[2]);
      // num features
      int numfeatures = splits.length - 3;
      newdoc.freqs = new int[numfeatures];
      newdoc.indices = new int[numfeatures];
      int lastPos = 0;
      for (int f = 3; f < splits.length; f++) {
        // x:y
        int sep = splits[f].indexOf(":");
        String pos = splits[f].substring(0, sep);
        String val = splits[f].substring(sep + 1);
        newdoc.indices[lastPos] = Integer.parseInt(pos);
        newdoc.freqs[lastPos] = Integer.parseInt(val);
        lastPos++;
      }
    } catch (Exception e) {
      return null;
    }
    return newdoc;
  }
  
  /**
   * this is done to make sure that the lexicon file will be read properly and
   * won't contain any characters that would break it
   **/
  private static String simpleNormalisationTokenString(String token) {
    return token.replaceAll("\\s+", "_");
  }
  
}
