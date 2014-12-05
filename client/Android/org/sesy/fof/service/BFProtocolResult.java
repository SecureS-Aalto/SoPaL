package org.sesy.fof.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import org.sesy.peershare.iface.SocialInfo;

import android.os.Parcel;
import android.os.Parcelable;

public class BFProtocolResult extends ProtocolResult
					implements Parcelable, Serializable {

	private List<ArrayList<SocialInfo> > commonFriends;
	private boolean directFriends;
	
	private static final String TAG = "BFProtocolResult";
	static final long serialVersionUID = 4209360273818925922L;
	
	public BFProtocolResult(String local_id, String remote_id, byte session_id, List<ArrayList<SocialInfo> > friends, SecretKey key, boolean direct) {
		super(local_id,remote_id,session_id,FoFAlgorithmType.BFPSI,key);
		//Log.d(TAG,"Setting result: direct=" + direct + " common: " + friends);
		this.commonFriends = friends;
		this.directFriends = direct;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeSerializable(this);
	}
	
	public static final Parcelable.Creator<BFProtocolResult> CREATOR= new Parcelable.Creator<BFProtocolResult>() {

		@Override
		public BFProtocolResult createFromParcel(Parcel source) {
			return new BFProtocolResult(source);
		}

		@Override
		public BFProtocolResult[] newArray(int size) {
			return new BFProtocolResult[size];
		}
	};
	
	@SuppressWarnings("unchecked")
	private BFProtocolResult(Parcel source) {
		super(source);
		this.commonFriends = (List<ArrayList<SocialInfo> >)source.readSerializable();
	}
	
	public List<ArrayList<SocialInfo> > getCommonFriends() {
		return this.commonFriends;
	}
	
	public boolean isDirectFriend() {
		return this.directFriends;
	}
}
