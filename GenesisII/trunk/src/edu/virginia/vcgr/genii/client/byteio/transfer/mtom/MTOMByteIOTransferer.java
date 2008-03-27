package edu.virginia.vcgr.genii.client.byteio.transfer.mtom;

import org.apache.axis.types.URI;

import edu.virginia.vcgr.genii.client.byteio.ByteIOConstants;

public interface MTOMByteIOTransferer
{
	static public final URI TRANSFER_PROTOCOL = 
		ByteIOConstants.TRANSFER_TYPE_MTOM_URI;
	
	static public final int PREFERRED_READ_SIZE = 1024 * 1024;
	static public final int PREFERRED_WRITE_SIZE = PREFERRED_READ_SIZE;
}