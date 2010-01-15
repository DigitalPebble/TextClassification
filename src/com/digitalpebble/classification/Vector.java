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

/* Contains a set of indices and values as doubles */
public class Vector {
  private int[] indices;
  private double[] values;
  
  public Vector(int[] indices, double[] values){
    this.indices = indices;
    this.values = values;
  }
  
  public int[] getIndices() {
    return indices;
  }

  public double[] getValues() {
    return values;
  }

}
