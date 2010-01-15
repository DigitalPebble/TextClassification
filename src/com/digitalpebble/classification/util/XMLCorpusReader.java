package com.digitalpebble.classification.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Field;
import com.digitalpebble.classification.FileTrainingCorpus;
import com.digitalpebble.classification.Learner;
import com.digitalpebble.classification.Parameters;

/** Converts a XML corpus into a raw file for training / testing **/
public class XMLCorpusReader extends DefaultHandler {

    File inputDir = null;
    FileTrainingCorpus filetrainingcorpus = null;
    Learner learner = null;
    XMLReader parser = null;
    static String regexpSplitter = "\\W";

    public XMLCorpusReader(String input, String output) throws Exception {
        inputDir = new File(input);
        learner = Learner.getLearner(output, Learner.LibSVMModelCreator, true);
        filetrainingcorpus = learner.getFileTrainingCorpus();
        learner.setMethod(Parameters.WeightingMethod.TFIDF);
        parser = XMLReaderFactory.createXMLReader();
        parser.setContentHandler(this);
    }

    private void iterateOnInput() {
        for (File child : inputDir.listFiles()) {
            if (child.getName().endsWith(".xml") == false)
                continue;
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
            XMLCorpusReader reader = new XMLCorpusReader(input, output);
            reader.iterateOnInput();
            reader.filetrainingcorpus.close();
            reader.learner.saveLexicon();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** XML sax events **/
    
    String currentLabel = null;
    String currentFieldName = null;
    StringBuffer currentString = new StringBuffer();
    ArrayList<Field> fields = new ArrayList<Field>();
    
    public void startElement(String nsURI, String strippedName, String tagName, Attributes attributes)
        throws SAXException {
        if (tagName.equalsIgnoreCase("document")) {
            String id = attributes.getValue("id");
            currentString = new StringBuffer();
            fields.clear();
        }
        else if (tagName.equalsIgnoreCase("field")) {
            currentFieldName = attributes.getValue("name");
            currentString = new StringBuffer();
        }
        else if (tagName.equalsIgnoreCase("label")) {
            currentString = new StringBuffer();
        }
    }
    
    public void endElement(String nsURI, String strippedName, String tagName) throws SAXException {
        // finished a document or field
        if (tagName.equalsIgnoreCase("document")) {
            // add the current doc to the corpus
            Field[] fs = (Field[])fields.toArray(new Field[fields.size()]);
            Document doc = learner.createDocument(fs, currentLabel);
            try {
                filetrainingcorpus.addDocument(doc);
            } catch (IOException e) {
                // can't add document 
                e.printStackTrace();
            }
        }
        else if (tagName.equalsIgnoreCase("field")) {
            // store the current field
            Field f = new Field(currentFieldName,currentString.toString().split(regexpSplitter)); 
            fields.add(f);
        }
        else if (tagName.equalsIgnoreCase("label")) {
            currentLabel = currentString.toString();
        }
    }

    public void characters(char[] str, int start, int length) {
        currentString.append(str, start, length);
    }

}
