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

import java.util.List;

/** Field name and its content **/

public class Field {

	String _name = null;
	String[] _tokens = null;

	public String getName() {
		return _name;
	}

	public Field(String name, List<String> tokens) {
		_name = name;
		_tokens = (String[]) tokens.toArray(new String[tokens.size()]);
	}

	public Field(String name, String[] tokens) {
		_name = name;
		_tokens = tokens;
	}

	public String[] getTokens() {
		return _tokens;
	}

	public String toString() {
		return _name + " : " + _tokens.length + " tokens";
	}

}
