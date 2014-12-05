package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class FinalControllerResponse extends FoFObject implements Parcelable, Serializable {

	public static final int DECISION_ADMIT = 1;
	public static final int DECISION_DENY = 2;
	
	private static final long serialVersionUID = 1L;
	
	private int decision;
	private byte[] encrypted_pass;
	
	public FinalControllerResponse(int dec, byte[] pass, String localid, String remoteid, byte sessionid) {
		super(localid,remoteid,sessionid);
		this.decision = dec;
		this.encrypted_pass = pass;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}
	
	public static final Parcelable.Creator<FinalControllerResponse> CREATOR= new Parcelable.Creator<FinalControllerResponse>() {

		@Override
		public FinalControllerResponse createFromParcel(Parcel source) {
			return (FinalControllerResponse) source.readSerializable();
		}

		@Override
		public FinalControllerResponse[] newArray(int size) {
			return new FinalControllerResponse[size];
		}
	};
	
	protected FinalControllerResponse(Parcel source) {
		super(source);
		FinalControllerResponse tmp = (FinalControllerResponse)source.readSerializable();
		this.decision = tmp.getAccessControlDecision();
		this.encrypted_pass = tmp.getEncryptedPassword();
	}
	
	public int getAccessControlDecision() {
		return this.decision;
	}
	
	public byte[] getEncryptedPassword() {
		return this.encrypted_pass;
	}

}
