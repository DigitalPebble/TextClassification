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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/*******************************************************************************
 * Keeps an equivalence between the id of an attribute and a score. An attribute
 * ID does not change whereas its position within a Lexicon may vary if filters
 * are applied
 ******************************************************************************/
public class AttributeScorer {

	private Map<Integer, Double> map;

	AttributeScorer() {
		map = new TreeMap<Integer, Double>();
	}

	public double getScore(int id) {
		Double dscore = map.get(id);
		if (dscore == null)
			return -1;
		return dscore.doubleValue();
	}

	public void setScore(int id, double score) {
		map.put(new Integer(id), new Double(score));
	}

	/***************************************************************************
	 * Returns the value of the nth score once sorted Used to determine a
	 * threshold
	 **************************************************************************/
	public double getValueForRank(int rank) {
		List<Double> scores = new ArrayList<Double>(map.values());
		java.util.Collections.sort(scores);
		return scores.get(scores.size() - rank);
	}

}
