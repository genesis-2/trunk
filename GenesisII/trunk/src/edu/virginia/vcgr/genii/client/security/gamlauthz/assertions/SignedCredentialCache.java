package edu.virginia.vcgr.genii.client.security.gamlauthz.assertions;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;

import edu.virginia.vcgr.genii.client.cache.LRUCache;

public class SignedCredentialCache
{

	// cache of signed, serialized delegation assertions
	static public int DELEGATION_CACHE_SIZE = 32;
	static public LRUCache<DelegatedAttribute, DelegatedAssertion> delegationAssertions =
			new LRUCache<DelegatedAttribute, DelegatedAssertion>(
					DELEGATION_CACHE_SIZE);

	public static DelegatedAssertion getCachedDelegateAssertion(
			DelegatedAttribute delegatedAttribute, PrivateKey privateKey)
			throws GeneralSecurityException
	{

		// check the cache to see if this assertion exists already
		synchronized (delegationAssertions)
		{
			DelegatedAssertion signedAssertion =
					delegationAssertions.get(delegatedAttribute);
			if (signedAssertion == null)
			{
				// not in cache: create a new delegated assertion
				signedAssertion =
						new DelegatedAssertion(delegatedAttribute, privateKey);

				delegationAssertions.put(delegatedAttribute, signedAssertion);
			}

			return signedAssertion;
		}

	}

}
