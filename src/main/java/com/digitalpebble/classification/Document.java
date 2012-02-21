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

import java.util.Map;

public interface Document {

  /**
   * Returns the label of the document. The String value of the label can be
   * accessed via the Lexicon object.*
   */
  public abstract int getLabel();

  /**
   * Returns a Vector representation of the document. This Vector object is used
   * by the instances of Learner or TextClassifier. The indices are sorted with
   * those filtered being at the end with a value of Integer.MAX_VALUE
   */
  public abstract Vector getFeatureVector(Lexicon lexicon);

  public abstract Vector getFeatureVector(Lexicon lexicon,
      Parameters.WeightingMethod method);
  
  /** 
   * Same as above but gives a mapping for the attributes numbers
   **/
  public abstract Vector getFeatureVector(Lexicon lexicon, Map<Integer, Integer> equiv);

  /**
   * Returns a String that can be used to serialize to/from a file
   */
  public abstract String getStringSerialization();

}