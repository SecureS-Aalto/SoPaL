package org.sesy.fof.service;

import java.io.Serializable;
import java.util.HashSet;

import android.os.Parcel;
import android.os.Parcelable;

public class BFNonceInitiatorContainer extends FoFObject implements Parcelable, Serializable {

	static final long serialVersionUID = 4209360273818925922L;
	
	private HashSet<String> rset;
	private String irand;
	
	public BFNonceInitiatorContainer(HashSet<String> rs, String ir, String localid, String remoteid, byte sessionid) {
		super(localid,remoteid,sessionid);
		this.rset = rs;
		this.irand = ir;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}
	
	public static final Parcelable.Creator<BFNonceInitiatorContainer> CREATOR= new Parcelable.Creator<BFNonceInitiatorContainer>() {

		@Override
		public BFNonceInitiatorContainer createFromParcel(Parcel source) {
			return new BFNonceInitiatorContainer(source);
		}

		@Override
		public BFNonceInitiatorContainer[] newArray(int size) {
			return new BFNonceInitiatorContainer[size];
		}
	};
	
	private BFNonceInitiatorContainer(Parcel source) {
		super(source);
		BFNonceInitiatorContainer ctr = (BFNonceInitiatorContainer)source.readSerializable();
		this.rset = ctr.getRSet();
		this.irand = ctr.getIRand();
	}
	
	public HashSet<String> getRSet() {
		return this.rset;
	}
	
	public String getIRand() {
		return this.irand;
	}
}
