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

package com.digitalpebble.classification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;


/*******************************************************************************
 * A TrainingCorpus encapsulates a list of Documents to be used for training and
 * stores their vector representation on a file. The values in this file are raw
 * i.e they are not modified by the weighting scheme. Using such a structure
 * allows to unload documents from memory once they've been added to the corpus.
 * In a future version we might be able to retrain a model by generating a
 * lexicon file straight from a 'raw' file thus avoiding the need for obtaining
 * the data in the first place.
 ******************************************************************************/
public class FileTrainingCorpus implements TrainingCorpus {

	private Writer raw_file_buffer;

	private File raw_file;

	/** Get a new TrainingCorpus or an existing one if there is one already there * */
	public FileTrainingCorpus(File rfile) throws IOException {
		// create a raw file in the working directory
		this.raw_file = rfile;
		
		if (rfile.exists()) {
			// try to load it
			try {
				Iterator iter = iterator();
				while (iter.hasNext()) {
					iter.next();
				}
			} catch (Exception e) {
				throw new IOException("Exception when reading existing raw file");
			}
		}

		raw_file_buffer = new BufferedWriter(new FileWriter(rfile,true));
	}

	// add a vectorial representation of the document to the file
	// there is exactly one document per line
	public void addDocument(Document doc) throws IOException {
		// needs to differenciate SimpleDocuments from MultiField ones
		// each class has its own way of serializing
		String serial = doc.getStringSerialization();
		raw_file_buffer.write(serial);
	}

	public void close() {
		try {
			raw_file_buffer.close();
		} catch (IOException e) {
		}
	}

	public Iterator<Document> iterator() {
		return new FileTrainingCorpusIterator(raw_file);
	}

}

// rebuild documents on the fly + iterate on them
class FileTrainingCorpusIterator implements java.util.Iterator<Document> {

	private BufferedReader reader;

	private Document cache;

	FileTrainingCorpusIterator(File f) {
		try {
			reader = new BufferedReader(new FileReader(f));
		} catch (FileNotFoundException e) {
		}
		// fill the cache
		fillCache();
	}

	private void fillCache() {
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				// convert line into document
				// if problem set cache to null
				Document freshDoc = null;
				if (line.startsWith("SimpleDocument")) {
					freshDoc = SimpleDocument.parse(line);
				} else if (line.startsWith("MultiFieldDocument")) {
					freshDoc = MultiFieldDocument.parse(line);
				}
				if (freshDoc != null) {
					cache = freshDoc;
					return;
				}
			}
		} catch (IOException e) {
			cache = null;
			return;
		}
		cache = null;
	}

	public boolean hasNext() {
		if (cache == null) {
			try {
				reader.close();
			} catch (IOException e) {
			}
			return false;
		}
		return true;
	}

	public Document next() {
		Document temp = cache;
		fillCache();
		return temp;
	}

	public void remove() {
		throw new RuntimeException("Remove operation not supported");
	}

}
