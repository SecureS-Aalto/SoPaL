package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class KeyDataContainer implements Parcelable, Serializable {

	private byte[] value;
	private String format;
	private String algorithm;
	
	static final long serialVersionUID = 4209360273818925922L;
	
	public KeyDataContainer() {
		this.value = null;
		this.format = null;
		this.algorithm = null;
	}
	
	public KeyDataContainer(byte[] key, String fmt, String algo) {
		this.value = key;
		this.format = fmt;
		this.algorithm = algo;
	}
	
	public static final Parcelable.Creator<KeyDataContainer> CREATOR = new Parcelable.Creator<KeyDataContainer>() {
		@Override
		public KeyDataContainer createFromParcel(Parcel source) {
			return new KeyDataContainer(source);
		}

		@Override
		public KeyDataContainer[] newArray(int size) {
			return new KeyDataContainer[size];
		}
	};
	
	private KeyDataContainer(Parcel source) {
		/*this.algorithm = source.readString();
		this.value = new byte[source.readInt()];
		source.readByteArray(this.value);
		this.format = source.readString();*/
		KeyDataContainer tmp = (KeyDataContainer)source.readSerializable();
		this.value = tmp.getEncodedKey();
		this.format = tmp.getFormat();
		this.algorithm = tmp.getAlgorithm();
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		/*dest.writeString(algorithm);
		dest.writeInt(value.length);
		dest.writeByteArray(value);
		dest.writeString(format);*/
		dest.writeSerializable(this);
	}
	
	public String getAlgorithm() {
		return this.algorithm;
	}
	
	public String getFormat() {
		return this.format;
	}
	
	public byte[] getEncodedKey() {
		return this.value;
	}
	
	/*PublicKey getKey() {
		return this.key;
	}*/
	
	

}
