package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class ResponderDataResponse extends FoFObject implements Parcelable, Serializable {

	private boolean accepted;
	private KeyDataContainer key;
	private FoFAlgorithmType type;
	static final long serialVersionUID = 4209360273818925922L;
	
	public ResponderDataResponse(KeyDataContainer key_, FoFAlgorithmType type_, boolean accept, 
			String localid, String remoteid, byte sessionid) {
		super(localid,remoteid,sessionid);
		this.key = key_;
		this.type = type_;
		this.accepted = accept;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}

	public static final Parcelable.Creator<ResponderDataResponse> CREATOR= new Parcelable.Creator<ResponderDataResponse>() {

		@Override
		public ResponderDataResponse createFromParcel(Parcel source) {
			return (ResponderDataResponse) source.readSerializable();
		}

		@Override
		public ResponderDataResponse[] newArray(int size) {
			return new ResponderDataResponse[size];
		}

	};

	protected ResponderDataResponse(Parcel source) {
		super(source);
		ResponderDataResponse tmp = (ResponderDataResponse)source.readSerializable();
		this.key = tmp.getPublicKey();
		this.type = tmp.getAlgorithmType();
		this.accepted = tmp.getRequestStatus();
	}

	public KeyDataContainer getPublicKey() {
		return this.key;
	}

	public FoFAlgorithmType getAlgorithmType() {
		return this.type;
	}
	
	public boolean getRequestStatus() {
		return this.accepted;
	}

	@Override
	public String toString() {
		return "key=" + this.key + " type=" + this.type;
	}
}
