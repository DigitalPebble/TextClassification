package com.digitalpebble.classification.test;

import com.digitalpebble.classification.Parameters;

public class TestNonEuropeanChars extends AbstractLearnerTest {

	public void testRussian() throws Exception {
		String[] title = new String[] { "Мастер", "и", "Маргарита" };
		learner.setMethod(Parameters.WeightingMethod.TFIDF);
		learner.createDocument(title, "test");

		// check the content of the lexicon
		assertEquals(3, learner.getLexicon().getAttributesNum());
		assertEquals(2,learner.getLexicon().getIndex("Мастер"));
	}
}
