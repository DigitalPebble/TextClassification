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

import java.util.Map;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Field;
import com.digitalpebble.classification.Parameters;
import com.digitalpebble.classification.RAMTrainingCorpus;
import com.digitalpebble.classification.TextClassifier;
import com.digitalpebble.classification.Vector;

public class TestMultiFieldDocs extends AbstractLearnerTest {

	public void testMultiField() throws Exception {
		Field[] fields = new Field[3];
		fields[0] = new Field("title", new String[] { "This", "is", "a",
				"title" });
		fields[1] = new Field("abstract", new String[] { "abstract" });
		fields[2] = new Field("content", new String[] { "This", "is", "the",
				"content", "this", "will", "have", "a", "large", "value" });
		learner.setMethod(Parameters.WeightingMethod.TFIDF);
		Document doc = learner.createDocument(fields, "large");

		Field[] fields2 = new Field[2];
		fields2[0] = new Field("title", new String[] { "This", "is", "not",
				"a", "title" });
		// fields2[1] = new Field("abstract", new String[]{"abstract"});
		fields2[1] = new Field("content", new String[] { "This", "is", "the",
				"content", "this", "will", "have", "a", "small", "value" });
		learner.setMethod(Parameters.WeightingMethod.TFIDF);
		Document doc2 = learner.createDocument(fields2, "small");

		// try putting the same field several times
		Field[] fields3 = new Field[3];
		fields3[0] = new Field("title", new String[] { "This", "is", "not",
				"a", "title" });
		// fields2[1] = new Field("abstract", new String[]{"abstract"});
		fields3[1] = new Field("content", new String[] { "This", "is", "the",
				"content", "this", "will", "have", "a", "small", "value" });
		fields3[2] = new Field("title", new String[] { "some", "different",
				"content" });
		learner.setMethod(Parameters.WeightingMethod.TFIDF);
		Document doc3 = learner.createDocument(fields3, "small");

		RAMTrainingCorpus corpus = new RAMTrainingCorpus();
		corpus.add(doc);
		corpus.add(doc2);
		learner.learn(corpus);

		TextClassifier classi = TextClassifier.getClassifier(tempFile);
		double[] scores = classi.classify(doc);
		assertEquals("large", classi.getBestLabel(scores));
		scores = classi.classify(doc2);
		assertEquals("small", classi.getBestLabel(scores));
		scores = classi.classify(doc3);
		assertEquals("small", classi.getBestLabel(scores));
	}

	public void testCustomWeightingScheme() throws Exception {
		Field[] fields = new Field[1];
		fields[0] = new Field("keywords", new String[] { "test","keywords"});
		learner.setMethod(Parameters.WeightingMethod.FREQUENCY);
		learner.getLexicon().setMethod(Parameters.WeightingMethod.BOOLEAN, "keywords");
		Document doc = learner.createDocument(fields, "large");
		Vector vector = doc.getFeatureVector(learner.getLexicon());
		
		// check that the values for the field keywords are boolean
		int[] indices = vector.getIndices();
		double[] values = vector.getValues();
		
		Map<Integer, String> invertedIndex = learner.getLexicon()
		.getInvertedIndex();
		
		for (int i = 0; i < indices.length; i++) {
			// retrieve the corresponding entry in the lexicon
			String label = invertedIndex.get(indices[i]);
			double expected = 1.0;
			assertEquals("label: "+label,expected, values[i]);
		}
	}

}
