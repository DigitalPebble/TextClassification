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

package com.digitalpebble.classification.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.FileTrainingCorpus;
import com.digitalpebble.classification.Lexicon;
import com.digitalpebble.classification.MultiFieldDocument;
import com.digitalpebble.classification.Parameters.WeightingMethod;
import com.digitalpebble.classification.libsvm.Utils;
import com.digitalpebble.classification.util.scorers.AttributeScorer;
import com.digitalpebble.classification.util.scorers.logLikelihoodAttributeScorer;

public class CorpusUtils {

    // rewrite a raw file so that only a subset of the fields are kept

    public static void filterFields(File trainingCorpus, File newRawFile,
            int[] fieldsToKeep) throws IOException {
        FileTrainingCorpus ftc = new FileTrainingCorpus(trainingCorpus);
        Writer writer = new BufferedWriter(new FileWriter(newRawFile));
        Iterator<Document> iterator = ftc.iterator();
        while (iterator.hasNext()) {
            MultiFieldDocument doc = (MultiFieldDocument) iterator.next();
            String representation = doc.getStringSerialization(fieldsToKeep);
            writer.write(representation);
        }
        writer.close();
    }

    public static void dumpBestAttributes(String raw, String lexiconF)
            throws IOException {
        // load the corpus + the lexicon
        // load the lexicon and the raw file
        Lexicon lexicon = new Lexicon(lexiconF);
        FileTrainingCorpus corpus = new FileTrainingCorpus(new File(raw));
        AttributeScorer scorer = logLikelihoodAttributeScorer.getScorer(corpus,
                lexicon);
    }

    /***************************************************************************
     * Takes a property file and generates new vector and lexicon files from an
     * existing lexicon and raw file
     * 
     * @throws IOException
     * @throws FileNotFoundException
     */
    public static void generateVectorFile(String raw, String lexiconF,
            File propertiesFile) throws FileNotFoundException, IOException {
        Properties props = new Properties();
        props.load(new FileInputStream(propertiesFile));

        String vector_location = props.getProperty("vector_location");
        String newLexicon = props.getProperty("new_lexicon_file");

        boolean compact = "true".equals(props
                .getProperty("compact.attribute.nums"));

        String format = props.getProperty("format");

        // load the lexicon and the raw file
        Lexicon lexicon = new Lexicon(lexiconF);

        String weightingScheme = props.getProperty(
                "classification_weight_scheme", "tfidf");
        WeightingMethod method = WeightingMethod
                .methodFromString(weightingScheme);
        lexicon.setMethod(method);

        // get the raw file
        FileTrainingCorpus ftc = new FileTrainingCorpus(new File(raw));

        int keepNBestAttributes = Integer.parseInt(props.getProperty(
                "keepNBestAttributes", "-1"));

        if (keepNBestAttributes != -1) {
            // double scores[] = logLikelihoodAttributeFilter.getScores(ftc,
            // lexicon);
            // lexicon.setLogLikelihoodRatio(scores);
            // lexicon.keepTopNAttributesLLR(keepNBestAttributes);
            AttributeScorer scorer = logLikelihoodAttributeScorer.getScorer(
                    ftc, lexicon);
            lexicon.setAttributeScorer(scorer);
            lexicon.applyAttributeFilter(scorer, keepNBestAttributes);
        } else {
            // apply the filters on the Lexicon
            int minFreq = Integer.parseInt(props
                    .getProperty("classification_minFreq"));
            int maxFreq = Integer.MAX_VALUE;

            lexicon.pruneTermsDocFreq(minFreq, maxFreq);
        }

        // change the indices of the attributes to remove
        // gaps between them
        Map<Integer, Integer> equiv = null;
        if (compact) {
            // create a new Lexicon object
            equiv = lexicon.compact();
        }

        // save the modified lexicon file
        if (newLexicon != null)
            lexicon.saveToFile(newLexicon);

        // dump a new vector file
        Utils.writeExamples(ftc, lexicon, true, vector_location, equiv, format);
    }

    /**
     * Generates a random sample of lines from an input file and stores the
     * selection in a file named like the original but with the suffix
     * "_"+number of lines.
     * 
     * @param input
     *            = input file
     * @param expected_number
     *            = number of lines to generate
     * @param noTest
     *            = indicates whether the lines which haven't been selected must
     *            be kept in a separate file e.g. for testing
     **/

    public static void randomSelection(File input, int expected_number,
            boolean noTest) throws IOException {
        BitSet biset = new BitSet();
        Random random = new java.util.Random();

        // find the number of lines in the original file
        int maxNumLines = 0;
        BufferedReader reader = new BufferedReader(new FileReader(input));
        while ((reader.readLine()) != null) {
            maxNumLines++;
        }

        System.out.println("Original file : " + maxNumLines + " lines");

        boolean dumpAll = maxNumLines <= expected_number;

        int added = 0;
        while (added <= expected_number) {
            int randomInt = random.nextInt(maxNumLines + 1);
            if (biset.get(randomInt) == false) {
                biset.set(randomInt, true);
                added++;
            }
        }
        // read the original raw file and generate a subset
        File output = new File(input.getParentFile(), input.getName() + "_"
                + expected_number);
        // put the rest in a file which we can use for eval
        File eval = new File(input.getParentFile(), input.getName() + "_"
                + expected_number + ".test");

        reader.close();
        reader = new BufferedReader(new FileReader(input));
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));
        BufferedWriter writer2 = null;
        if (noTest == false)
            writer2 = new BufferedWriter(new FileWriter(eval));

        int count = 0;
        int kept = 0;
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (biset.get(count) || dumpAll) {
                writer.append(line + "\n");
                kept++;
            } else if (noTest == false) {
                writer2.append(line + "\n");
            }
            count++;
        }

        System.out.println("New file : " + kept + " lines");

        if (noTest == false)
            writer2.close();
        writer.close();
        reader.close();

    }

    public static void main(String[] args) {

        if (args.length < 2) {
            StringBuffer buffer = new StringBuffer();
            buffer.append("CorpusUtil : \n");
            buffer.append("\t -compactAttributes existingRawFile newRawFile lexiconFile newLexiconFile\n");
            buffer.append("\t -filterFields existingRawFile newRawFile [field number]+ \n");
            buffer.append("\t -generateVector rawFile lexicon parameter_file\n");
            buffer.append("\t -randomSelection rawFile expected_num_lines [-noTest]\n");
            buffer.append("\t -bestAttributes rawFile lexicon\n");
            System.out.println(buffer.toString());
            return;
        }

        else if (args[0].equalsIgnoreCase("-generateVector")) {
            String fileName = args[1];
            String lexicon = args[2];
            String params = args[3];
            try {
                generateVectorFile(fileName, lexicon, new File(params));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        else if (args[0].equalsIgnoreCase("-bestAttributes")) {
            String fileName = args[1];
            String lexicon = args[2];
            try {
                dumpBestAttributes(fileName, lexicon);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        else if (args[0].equalsIgnoreCase("-filterFields")) {
            // get the input file
            // followed by the references of fields to keep
            String fileName = args[1];
            String newFileName = args[2];
            int lastPosition = 3;
            int[] fieldNumArray = new int[args.length - lastPosition];
            for (int i = lastPosition; i < args.length; i++) {
                int fieldNum = Integer.parseInt(args[i]);
                fieldNumArray[i - lastPosition] = fieldNum;
            }
            try {
                filterFields(new File(fileName), new File(newFileName),
                        fieldNumArray);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        else if (args[0].equalsIgnoreCase("-randomSelection")) {
            String fileName = args[1];
            int expected = Integer.parseInt(args[2]);
            boolean noTest = false;
            if (args.length >= 4)
                if ("-noTest".equals(args[3]))
                    noTest = true;
            try {
                randomSelection(new File(fileName), expected, noTest);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
