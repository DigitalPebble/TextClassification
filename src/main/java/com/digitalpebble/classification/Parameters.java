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

public abstract class Parameters {

	public enum WeightingMethod {
		FREQUENCY, BOOLEAN, TFIDF, OCCURRENCES;

		public String toString() {
			switch (this) {
			case FREQUENCY:
				return "frequency";
			case BOOLEAN:
				return "boolean";
			case TFIDF:
				return "tfidf";
			case OCCURRENCES:
				return "occurrences";
			}
			throw new AssertionError("Unknown weighting scheme value :" + this);
		}

		public static WeightingMethod methodFromString(String name) {
			if ("frequency".equalsIgnoreCase(name))
				return FREQUENCY;
			if ("boolean".equalsIgnoreCase(name))
				return BOOLEAN;
			if ("tfidf".equalsIgnoreCase(name))
				return TFIDF;
			if ("occurrences".equalsIgnoreCase(name))
				return OCCURRENCES;
			else {
				throw new RuntimeException("Unknown weighting scheme name : "
						+ name);
			}
		}

	}

	public static String modelName = "model";

	public static String lexiconName = "lexicon";

	public static String vectorName = "vector";

	public static String rawName = "raw";

}
