package edu.virginia.vcgr.genii.client.security.gamlauthz.assertions;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.security.cert.X509Certificate;

import edu.virginia.vcgr.genii.client.security.gamlauthz.identity.*;

/**
 * Simple base class for GAML "identity" attributes.
 */
public class IdentityAttribute extends BasicAttribute {
	
	static public final long serialVersionUID = 0L;

	protected AssertableIdentity _identity;
	
	// zero-arg contstructor for externalizable use only!
	public IdentityAttribute() {}
	
	public IdentityAttribute(
			long notValidBeforeMillis, 
			long durationValidMillis, 
			long maxDelegationDepth,
			AssertableIdentity identity) {
		super(
				notValidBeforeMillis, 
				durationValidMillis, 
				maxDelegationDepth);
		_identity = identity;
	}
	
	public X509Certificate[] getAssertingIdentityCertChain() {
		return _identity.getAssertingIdentityCertChain();
	}
	
	public Identity getIdentity() {
		return _identity;
	}
	
	public String toString() {
		return "(IdentityAttribute) beforeMillis: " + this._notValidBeforeMillis + 
			" durationValidMillis: " + this._durationValidMillis + 
			" maxDelegationDepth: " + this._maxDelegationDepth + 
			" identity(" + _identity.getAssertingIdentityCertChain().length + "): [" + _identity + "]"; 
	}
	
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeObject(_identity);
	}

	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		_identity = (AssertableIdentity) in.readObject();
	}	
	
}
