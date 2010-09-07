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

import java.io.IOException;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Parameters;
import com.digitalpebble.classification.TextClassifier;
import com.digitalpebble.classification.Vector;

public class LibSVMClassifier extends TextClassifier {
  svm_model model;

  protected final void loadModel() throws IOException {
    // location of the model
    String modelPath = pathResourceDirectory + java.io.File.separator
            + Parameters.modelName;
    model = svm.svm_load_model(modelPath);
  }

  public final double[] classify(Document document) throws Exception {
    int svm_type = svm.svm_get_svm_type(model);
    int nr_class = svm.svm_get_nr_class(model);
    int[] labels = new int[nr_class];
    svm.svm_get_labels(model, labels);
    boolean support_probabilities = svm.svm_check_probability_model(model) == 1;
    double[] scores = new double[nr_class];
    // creates nodes from document
    Vector vector = document.getFeatureVector(this.lexicon);
    int[] indices = vector.getIndices();
    double[] values = vector.getValues();
    svm_node[] svm_nodes = new svm_node[indices.length];
    for(int n = 0; n < svm_nodes.length; n++) {
      svm_nodes[n] = new svm_node();
      svm_nodes[n].index = indices[n];
      svm_nodes[n].value = values[n];
    }
    if(support_probabilities) // returns the real probabilities
    {
      svm.svm_predict_probability(model, svm_nodes, scores);
      return scores;
    }
    // or gives 100% to the best label
    int winner = (int)svm.svm_predict(model, svm_nodes);
    // the array should contain a list of continuous values
    // otherwise it means that the lexicon and the model don't 
    // have the same number of labels
    // if this was the case we just make the array larger 
    // so that the values match those of the lexicon
    if (scores.length<=winner) scores = new double[winner+1];
    scores[winner] = 100d;
    return scores;
  }
}
