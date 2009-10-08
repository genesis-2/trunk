package edu.virginia.vcgr.genii.container.q2.matching;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class DefaultMatchingParameter extends MatchingParameter
{
	static private Log _logger = LogFactory.getLog(DefaultMatchingParameter.class);
	
	private String _property;
	private String _value;
	
	DefaultMatchingParameter(String property, String value)
	{
		_property = property;
		_value = value;
		
	}
	
	@Override
	final boolean matches(Map<String, Collection<String>> properties)
	{
		if (_property == null)
		{
			_logger.warn("Found a matching parameter property with no name.");
			return true;
		}
		
		if (_value == null)
			_logger.trace("Found a matching parameter property with no " +
				"value...assuming it's a set test.");
		
		Collection<String> values = properties.get(_property);
		if (values == null || values.size() == 0)
			return false;
		
		if (_value == null)
			return true;
		
		for (String value : values)
		{
			if (value.equals(_value))
				return true;
		}
		
		return false;
	}
	
	@Override
	public String toString()
	{
		return String.format("%s = %s", _property, _value);
	}
}