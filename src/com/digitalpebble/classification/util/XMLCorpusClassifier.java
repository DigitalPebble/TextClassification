package com.digitalpebble.classification.util;

import java.io.File;
import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Field;
import com.digitalpebble.classification.TextClassifier;

/** Classifiy docs represented as a XML corpus **/
public class XMLCorpusClassifier extends DefaultHandler {
  
  File inputDir = null;
  TextClassifier classifier = null;
  XMLReader parser = null;
  static String regexpSplitter = "\\W";
  
  public XMLCorpusClassifier(String input, String resourceDir) throws Exception {
    inputDir = new File(input);
    classifier = TextClassifier.getClassifier(resourceDir);
    parser = XMLReaderFactory.createXMLReader();
    parser.setContentHandler(this);
  }
  
  private void iterateOnInput() {
    for (File child : inputDir.listFiles()) {
      if (child.getName().endsWith(".xml") == false) continue;
      try {
        parser.parse(child.getPath());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  public static void main(String[] args) {
    String input = args[0];
    String output = args[1];
    try {
      XMLCorpusClassifier reader = new XMLCorpusClassifier(input, output);
      reader.iterateOnInput();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  /** XML sax events **/
  
  String currentLabel = null;
  String currentFieldName = null;
  StringBuffer currentString = new StringBuffer();
  ArrayList<Field> fields = new ArrayList<Field>();
  
  public void startElement(String nsURI, String strippedName, String tagName,
      Attributes attributes) throws SAXException {
    if (tagName.equalsIgnoreCase("document")) {
      String id = attributes.getValue("id");
      System.out.println("doc "+id);
      currentString = new StringBuffer();
      fields.clear();
    } else if (tagName.equalsIgnoreCase("field")) {
      currentFieldName = attributes.getValue("name");
      currentString = new StringBuffer();
    } else if (tagName.equalsIgnoreCase("label")) {
      currentString = new StringBuffer();
    }
  }
  
  public void endElement(String nsURI, String strippedName, String tagName)
      throws SAXException {
    // finished a document or field
    if (tagName.equalsIgnoreCase("document")) {
      // add the current doc to the corpus
      Field[] fs = (Field[]) fields.toArray(new Field[fields.size()]);
      Document doc = classifier.createDocument(fs);
      try {
        double[] scores = classifier.classify(doc);
        System.out.println("best label > "+classifier.getBestLabel(scores));
        for (double score : scores){
          System.out.println(score);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else if (tagName.equalsIgnoreCase("field")) {
      // store the current field
      Field f = new Field(currentFieldName, currentString.toString().split(
          regexpSplitter));
      fields.add(f);
    } else if (tagName.equalsIgnoreCase("label")) {
      currentLabel = currentString.toString();
    }
  }
  
  public void characters(char[] str, int start, int length) {
    currentString.append(str, start, length);
  }
  
}
