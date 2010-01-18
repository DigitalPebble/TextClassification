package com.digitalpebble.classification.util;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import liblinear.Model;

import com.digitalpebble.classification.Lexicon;

public class ModelUtils {
  
  public static void getAttributeScores(String modelPath, String lexiconF)
      throws IOException {
    // load the model + the lexicon
    // try to see if we can get a list of the best scores from the model
    // works only for liblinear
    Lexicon lexicon = new Lexicon(lexiconF);
    Model liblinearModel = Model.load(new File(modelPath));
    double[] weights = liblinearModel.getFeatureWeights();
    // dump all the weights
    int numClasses = liblinearModel.getNrClass();
    int numFeatures = liblinearModel.getNrFeature();
    
    Map<Integer, String> invertedAttributeIndex = lexicon.getInvertedIndex();
    
    for (int i = 0; i< weights.length; i++){
      // get current class num
      int classNum = i / numFeatures;
      int featNum = i % numFeatures;
      String classLabel = lexicon.getLabel(classNum);
      String attLabel = invertedAttributeIndex.get(featNum+1);
      System.out.println(attLabel+"\t"+classLabel+"\t"+weights[i]);
    }
  }
  
  public static void main(String[] args) {
    if (args.length < 2) {
      StringBuffer buffer = new StringBuffer();
      buffer.append("ModelUtils : \n");
      buffer.append("\t -getAttributeScores modeFile lexicon\n");
      System.out.println(buffer.toString());
      return;
    }

    else if (args[0].equalsIgnoreCase("-getAttributeScores")) {
      String model = args[1];
      String lexicon = args[2];
      try {
        getAttributeScores(model, lexicon);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
  }
  
}
