/*
 * This code was developed by Mark Morgan (mmm2a@virginia.edu) at the University of Virginia and is an implementation of JSDL, JSDL
 * ParameterSweep and other JSDL related specifications from the OGF.
 * 
 * Copyright 2010 University of Virginia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package edu.virginia.vcgr.jsdl.mapping;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;

import edu.virginia.vcgr.jsdl.OperatingSystemNames;

/**
 * @author Mark Morgan (mmm2a@virginia.edu)
 */
class OSMappingElement
{
	@XmlAttribute(name = "java-value", required = true)
	private String _javaValue;

	@XmlAttribute(name = "jsdl-value", required = true)
	private OperatingSystemNames _os;

	@SuppressWarnings("unused")
	private void afterUnmarshal(Unmarshaller u, Object parent) throws JAXBException
	{
		if (_javaValue == null)
			throw new JAXBException("Error trying to xml unmarshall Architecture mapping:  " + "java-value was null.");

		if (_os == null)
			throw new JAXBException("Error trying to xml unmarshall Operating System name mapping:  " + "jsdl-value was null.");
	}

	/**
	 * For use by XML deserialization only.
	 */
	@SuppressWarnings("unused")
	private OSMappingElement()
	{
	}

	OSMappingElement(String javaValue, OperatingSystemNames os)
	{
		_javaValue = javaValue;
		_os = os;
	}

	final public String javaValue()
	{
		return _javaValue;
	}

	final OperatingSystemNames os()
	{
		return _os;
	}
}
