package com.digitalpebble.classification.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.digitalpebble.classification.Document;
import com.digitalpebble.classification.Parameters;
import com.digitalpebble.classification.Parameters.WeightingMethod;
import com.digitalpebble.classification.RAMTrainingCorpus;
import com.digitalpebble.classification.Vector;

/**
 * Build dummy Documents and obtain weighted vectors
 **/

public class TestWeightingSchemes extends AbstractLearnerTest {

	private static String[][] docs;

	private static Map<WeightingMethod, List<Map>> references;

	static {
		docs = new String[][] { new String[] { "a", "b", "a", "c", "d" },
				new String[] { "b", "b", "a", "c", "e" },
				new String[] { "a", "b", "c", "f" } };

		references = new HashMap<WeightingMethod, List<Map>>();

		buildRefForBooleanMetric();
		buildRefForOccMetric();
		buildRefForFreqMetric();
		// TODO build ref for tf-idf
	}

	private static void buildRefForFreqMetric() {
		List<Map> expectedFrequency = new ArrayList<Map>();
		references.put(Parameters.WeightingMethod.FREQUENCY, expectedFrequency);

		HashMap<String, Double> freq1 = new HashMap<String, Double>();
		expectedFrequency.add(freq1);
		freq1.put("a", 0.4);
		freq1.put("b", 0.2);
		freq1.put("c", 0.2);
		freq1.put("d", 0.2);

		HashMap<String, Double> freq2 = new HashMap<String, Double>();
		expectedFrequency.add(freq2);
		freq2.put("a", 0.2);
		freq2.put("b", 0.4);
		freq2.put("c", 0.2);
		freq2.put("e", 0.2);

		HashMap<String, Double> freq3 = new HashMap<String, Double>();
		expectedFrequency.add(freq3);
		freq3.put("a", 0.25);
		freq3.put("b", 0.25);
		freq3.put("c", 0.25);
		freq3.put("f", 0.25);
	}

	private static void buildRefForBooleanMetric() {
		// references for boolean
		List<Map> expectedList = new ArrayList<Map>();
		references.put(Parameters.WeightingMethod.BOOLEAN, expectedList);

		HashMap<String, Double> map1 = new HashMap<String, Double>();
		expectedList.add(map1);
		map1.put("a", 1.0);
		map1.put("b", 1.0);
		map1.put("c", 1.0);
		map1.put("d", 1.0);

		HashMap<String, Double> map2 = new HashMap<String, Double>();
		expectedList.add(map2);
		map2.put("a", 1.0);
		map2.put("b", 1.0);
		map2.put("c", 1.0);
		map2.put("e", 1.0);

		HashMap<String, Double> map3 = new HashMap<String, Double>();
		expectedList.add(map3);
		map3.put("a", 1.0);
		map3.put("b", 1.0);
		map3.put("c", 1.0);
		map3.put("f", 1.0);
	}

	private static void buildRefForOccMetric() {
		// references for boolean
		List<Map> expectedOccu = new ArrayList<Map>();
		references.put(Parameters.WeightingMethod.OCCURRENCES, expectedOccu);

		HashMap<String, Double> occu1 = new HashMap<String, Double>();
		expectedOccu.add(occu1);
		occu1.put("a", 2.0);
		occu1.put("b", 1.0);
		occu1.put("c", 1.0);
		occu1.put("d", 1.0);

		HashMap<String, Double> occu2 = new HashMap<String, Double>();
		expectedOccu.add(occu2);
		occu2.put("a", 1.0);
		occu2.put("b", 2.0);
		occu2.put("c", 1.0);
		occu2.put("e", 1.0);

		HashMap<String, Double> occu3 = new HashMap<String, Double>();
		expectedOccu.add(occu3);
		occu3.put("a", 1.0);
		occu3.put("b", 1.0);
		occu3.put("c", 1.0);
		occu3.put("f", 1.0);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testBooleanWeightingSchemes() {
		evaluateWeightingSchemes(Parameters.WeightingMethod.BOOLEAN);
	}

	public void testOccurWeightingSchemes() {
		evaluateWeightingSchemes(Parameters.WeightingMethod.OCCURRENCES);
	}

	public void testFrequencyWeightingSchemes() {
		evaluateWeightingSchemes(Parameters.WeightingMethod.FREQUENCY);
	}

	private void evaluateWeightingSchemes(WeightingMethod method) {

		RAMTrainingCorpus corpus = new RAMTrainingCorpus();

		learner.setMethod(method);

		for (String[] content : docs) {
			Document doc = learner.createDocument(content);
			corpus.add(doc);
		}

		Iterator<Document> corpusIter = corpus.iterator();

		Map<Integer, String> invertedIndex = learner.getLexicon()
				.getInvertedIndex();

		List<Map> expectedset = references.get(method);

		// check that we have the same number of docs in the corpus
		// and in the ref

		assertEquals(expectedset.size(), corpus.size());

		for (Map<String, Double> ref : expectedset) {
			Document doc = corpusIter.next();
			Vector vector = doc.getFeatureVector(learner.getLexicon());
			// now let's compare what we wanted to have with the content of the
			// vector
			int[] indices = vector.getIndices();
			double[] values = vector.getValues();

			// compare size of indices with reference
			assertEquals(ref.size(), indices.length);

			for (int i = 0; i < indices.length; i++) {
				// retrieve the corresponding entry in the lexicon
				String label = invertedIndex.get(indices[i]);
				double expected = ref.get(label);
				assertEquals(expected, values[i]);
			}
		}
	}

}
