package com.digitalpebble.classification.util;

import java.io.File;
import java.io.IOException;

import com.digitalpebble.classification.Lexicon;

public class ModelUtils {
  
  public static void getAttributeScores(String model, String lexiconF)
      throws IOException {
    // load the model + the lexicon
    // try to see if we can get a list of the best scores from the model
    Lexicon lexicon = new Lexicon(lexiconF);
    
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
