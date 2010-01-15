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

package com.digitalpebble.classification.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Learner;
import com.digitalpebble.classification.Parameters;
import com.digitalpebble.classification.RAMTrainingCorpus;
import com.digitalpebble.classification.TextClassifier;

public class classifyDoc extends TestCase {
  public static void main(String[] args) {
    junit.textui.TestRunner.run(classifyDoc.class);
  }
  
  public void testcreateModel() throws Exception{
    // opens a file containing one sentence per line and
    // generates a model out of it
    String fileSubj = "corpus/quote.tok.gt9.5000";
    String fileObj = "corpus/plot.tok.gt9.5000";
    
    String parameters = "-s 0 -t 0";
    
    // if temp directory does not exists
    // create it
    File directory = new File("./temp");
    if(directory.exists() == false){
     new File("./temp").mkdir();
    }    
    Learner creator = Learner.getLearner("./temp",Learner.LibSVMModelCreator,true);
    creator.setParameters(parameters);
    creator.setMethod(Parameters.WeightingMethod.BOOLEAN);

    RAMTrainingCorpus subjectiveCorpus = getCorpus(fileSubj,"subjective",creator);
    RAMTrainingCorpus objectiveCorpus = getCorpus(fileObj,"objective",creator);
    subjectiveCorpus.addAll(objectiveCorpus);
    
    // filter some of the attributes out based on their LLR score
    creator.keepTopNAttributesLLR(2000);
    
    // prune terms
    // creator.pruneTermsDocFreq(2,subjectiveCorpus.size());
    
    long l0 = System.currentTimeMillis();
    creator.learn(subjectiveCorpus);
    long l1 = System.currentTimeMillis();
    System.err.println("learning done in "+(l1-l0));
  }
  
  public void testUseModel() throws Exception{
	    String fileSubj = "corpus/quote.tok.gt9.5000";
	    String fileObj = "corpus/plot.tok.gt9.5000";

    TextClassifier applier = TextClassifier.getClassifier("./temp");
    
    List subjectiveCorpus = getCorpus(fileSubj,"subjective",applier);
    List objectiveCorpus = getCorpus(fileObj,"objective",applier);
   
    long l0 = System.currentTimeMillis();
    
    double[][] scores = applier.classify(subjectiveCorpus);
    int totalDocs = scores.length;
    int totalcorrect = 0;
    int correct = 0;
    // this one should be positive
    for (int i=0;i<scores.length;i++){
      if (scores[i][0]>scores[i][1])correct++;
    }
    totalcorrect=correct;
    System.out.println("----- Subjectives -----");
    System.out.println(correct+" correct");
    
    scores = applier.classify(objectiveCorpus);
    correct = 0;
    totalDocs += scores.length;
    // this one should be negative
    for (int i=0;i<scores.length;i++){
      if (scores[i][0]<scores[i][1])correct++;
    }
    System.out.println("----- Objective -----");
    System.out.println(correct+" correct");
    totalcorrect+=correct;
    
    long l1 = System.currentTimeMillis();
    double overallPerf = (double)totalcorrect/(double)totalDocs;
    System.out.println("overall perfs "+overallPerf);
    System.out.println("classification done in "+(l1-l0));
    
    
    // delete the temp directory
    File directory = new File("./temp");
    if(directory.exists()){
      removeDirectory(directory);
    }    
    
  }
  
  public static void removeDirectory(File directory) {
    if(directory.isDirectory() == false) return;
    File[] content = directory.listFiles();
    for(int i = 0; i < content.length; i++) {
      if(content[i].isDirectory())
        removeDirectory(content[i]);
      else content[i].delete();
    }
    directory.delete();
  }
  
   
  private RAMTrainingCorpus getCorpus(String file, String label, Object operator) throws IOException{
    File original = new File(file);
    BufferedReader reader = new BufferedReader(new FileReader(original));
    String line;
    RAMTrainingCorpus corpusList = new RAMTrainingCorpus();
    while ((line=reader.readLine())!=null)
    {
      String[] tokens = line.split("\\W");
      // lower case
      for (int i=0;i<tokens.length;i++){
        tokens[i]=tokens[i].toLowerCase();
      }      
      
      Document doc = null;
      if (operator instanceof Learner)
      doc = ((Learner)operator).createDocument(tokens,label);
      else 
        doc = ((TextClassifier)operator).createDocument(tokens);
      corpusList.add(doc);
    }
    reader.close();
    return corpusList;
  }
   
  
}
