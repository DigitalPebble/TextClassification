package com.digitalpebble.classification.liblinear;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import liblinear.FeatureNode;
import liblinear.Linear;
import liblinear.Model;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Parameters;
import com.digitalpebble.classification.TextClassifier;
import com.digitalpebble.classification.Vector;

public class LibLinearClassifier extends TextClassifier {
  
  Model liblinearModel;
  
  private static boolean flag_predict_probability = false;
  
  public double[] classify(Document document) throws Exception {
    int nr_class = liblinearModel.getNrClass();
    double[] prob_estimates = new double[nr_class];
    int n;
    int nr_feature = liblinearModel.getNrFeature();
    if (liblinearModel.getBias() >= 0) n = nr_feature + 1;
    else n = nr_feature;
    
    int[] labels = liblinearModel.getLabels();
    
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
      FeatureNode node = new FeatureNode(n, liblinearModel.getBias());
      x.add(node);
    }
    
    FeatureNode[] nodes = new FeatureNode[x.size()];
    nodes = x.toArray(nodes);
    
    if (flag_predict_probability) {
      Linear.predictProbability(liblinearModel, nodes, prob_estimates);
      return prob_estimates;
    }
    int predict_label = Linear.predict(liblinearModel, nodes);
    if (prob_estimates.length <= predict_label) prob_estimates = new double[prob_estimates.length + 1];
    prob_estimates[predict_label] = 1d;
    return prob_estimates;
  }
  
  protected void loadModel() throws Exception {
    String modelPath = pathResourceDirectory + java.io.File.separator
        + Parameters.modelName;
    liblinearModel = Model.load(new File(modelPath));
  }
  
}
