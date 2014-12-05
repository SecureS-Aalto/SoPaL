package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class FoFAlgorithmType implements Parcelable, Serializable {

	public static final int PSI = 1;
	public static final int PSI_CA = 2;
	public static final int BFPSI = 3;
	//public static final int BF_TWO_WAY = 4;
	
	private static final long serialVersionUID = 0x98F22BF5;
	
	private int friendshipDepth;
	private int algorithm;
	private int friendsNumber;
	
	public FoFAlgorithmType(int depth, int algo, int amount) {
		this.friendshipDepth = depth;
		this.algorithm = algo;
		this.friendsNumber = amount;
	}
	
	public FoFAlgorithmType(int algo, int amount) {
		this.friendshipDepth = 1;
		this.algorithm = algo;
		this.friendsNumber = amount;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(friendshipDepth);
		dest.writeInt(algorithm);
		dest.writeInt(friendsNumber);
	}
	
	public static final Parcelable.Creator<FoFAlgorithmType> CREATOR= new Parcelable.Creator<FoFAlgorithmType>() {

		@Override
		public FoFAlgorithmType createFromParcel(Parcel source) {
			return new FoFAlgorithmType(source);
		}

		@Override
		public FoFAlgorithmType[] newArray(int size) {
			return new FoFAlgorithmType[size];
		}

	};
	
	private FoFAlgorithmType(Parcel source) {
		this.friendshipDepth = source.readInt();
		this.algorithm = source.readInt();
		this.friendsNumber = source.readInt();
	}
	
	public int getFriendshipDepth() {
		return this.friendshipDepth;
	}
	
	public int getAlgorithmType() {
		return this.algorithm;
	}
	
	public int getFriendsNumber() {
		return this.friendsNumber;
	}
	
	public String toString() {
		return "algo=" + algorithm + " depth=" + friendshipDepth + " amount=" + friendsNumber;
	}

}
