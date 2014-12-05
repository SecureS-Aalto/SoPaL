package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcelable;

public class BFResponderDataResponse extends ResponderDataResponse implements
		Parcelable, Serializable {

	static final long serialVersionUID = 4209360273818925922L;
	public BFResponderDataResponse(KeyDataContainer key_, FoFAlgorithmType type_, boolean accept, 
			String localid, String remoteid, byte sessionid) {
		super(key_,type_,accept,localid,remoteid,sessionid);
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
}
