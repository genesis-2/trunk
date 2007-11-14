package edu.virginia.vcgr.genii.client.cache;

import java.util.Date;

class RoleBasedCacheNode<KeyType, DataType>
{
	static public final int ROLE_LRU = 0;
	static public final int ROLE_TIMEOUT = 1;
	static private final int _NUM_ROLES = 2;
	
	private Object []_next;
	private Object []_previous;
	
	private KeyType _key;
	private DataType _data;
	
	private Date _invalidationDate;
	
	public RoleBasedCacheNode(KeyType key, DataType data, Date invalidationDate)
	{
		_next = new Object[_NUM_ROLES];
		_previous = new Object[_NUM_ROLES];
		
		for (int lcv = 0; lcv < _NUM_ROLES; lcv++)
		{
			_next[lcv] = null;
			_previous[lcv] = null;
		}
		
		_key = key;
		_data = data;
		_invalidationDate = invalidationDate;
	}
	
	public KeyType getKey()
	{
		return _key;
	}
	
	public DataType getData()
	{
		return _data;
	}
	
	public Date getInvalidationDate()
	{
		return _invalidationDate;
	}
	
	@SuppressWarnings("unchecked")
	public RoleBasedCacheNode<KeyType, DataType> getPrevious(int role)
	{
		return RoleBasedCacheNode.class.cast(_previous[role]);
	}
	
	@SuppressWarnings("unchecked")
	public RoleBasedCacheNode<KeyType, DataType> getNext(int role)
	{
		return RoleBasedCacheNode.class.cast(_next[role]);
	}
	
	public void setPrevious(int role, RoleBasedCacheNode<KeyType, DataType> previous)
	{
		_previous[role] = previous;
	}
	
	public void setNext(int role, RoleBasedCacheNode<KeyType, DataType> next)
	{
		_next[role] = next;
	}
	
	public void clearLinks(int role)
	{
		_previous[role] = null;
		_next[role] = null;
	}
	
	public void clearLinks()
	{
		for (int lcv = 0; lcv < _NUM_ROLES; lcv++)
			clearLinks(lcv);
	}
}