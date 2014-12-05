package org.sesy.fof.service;

import java.io.Serializable;
import java.util.HashSet;

import android.os.Parcel;
import android.os.Parcelable;

public class BFNonceContainer extends FoFObject implements Parcelable, Serializable {

	static final long serialVersionUID = 4209360273818925922L;
	
	private HashSet<String> cset;
	private String ckey;
	private String rrand;
	
	public BFNonceContainer(HashSet<String> cs, String ck, String rr, String localid, String remoteid, byte sessionid) {
		super(localid,remoteid,sessionid);
		this.cset = cs;
		this.ckey = ck;
		this.rrand = rr;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}
	
	public static final Parcelable.Creator<BFNonceContainer> CREATOR= new Parcelable.Creator<BFNonceContainer>() {

		@Override
		public BFNonceContainer createFromParcel(Parcel source) {
			return new BFNonceContainer(source);
		}

		@Override
		public BFNonceContainer[] newArray(int size) {
			return new BFNonceContainer[size];
		}
	};
	
	private BFNonceContainer(Parcel source) {
		super(source);
		BFNonceContainer ctr = (BFNonceContainer)source.readSerializable();
		this.cset = ctr.getCSet();
		this.ckey = ctr.getCKey();
		this.rrand = ctr.getRRand();
	}

	public HashSet<String> getCSet() {
		return this.cset;
	}
	
	public String getCKey() {
		return this.ckey;
	}
	
	public String getRRand() {
		return this.rrand;
	}	
}
