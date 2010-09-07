package com.digitalpebble.classification.test;

import java.io.File;

import junit.framework.TestCase;

import com.digitalpebble.classification.Learner;

public abstract class AbstractLearnerTest extends TestCase {

	Learner learner;
	File tempFile;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		// build a Learner
		File tempFile2 = java.io.File.createTempFile("TextClassifier", "");
		tempFile2.delete();
		tempFile = new File(tempFile2.getParentFile(), "TextClassifierDir");
		tempFile.mkdir();
		learner = Learner.getLearner(tempFile.getAbsolutePath(),
				Learner.LibSVMModelCreator, true);
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		removeDirectory(tempFile);
		learner = null;
	}

	private static void removeDirectory(File directory) {
		if (directory.isDirectory() == false)
			return;
		File[] content = directory.listFiles();
		for (int i = 0; i < content.length; i++) {
			if (content[i].isDirectory())
				removeDirectory(content[i]);
			else
				content[i].delete();
		}
		directory.delete();
	}

}
