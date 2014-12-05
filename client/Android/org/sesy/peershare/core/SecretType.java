package org.sesy.peershare.core;

import android.os.Parcel;
import android.os.Parcelable;

public enum SecretType implements Parcelable {
	
	BT_MAC,
	WIFI_MAC;
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel arg0, int arg1) {
		arg0.writeInt(ordinal());
	}
	
    public static final Creator<SecretType> CREATOR = new Creator<SecretType>() {
        @Override
        public SecretType createFromParcel(final Parcel source) {
            return SecretType.values()[source.readInt()];
        }

        @Override
        public SecretType[] newArray(final int size) {
            return new SecretType[size];
        }
    };
}