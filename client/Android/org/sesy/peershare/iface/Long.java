package org.sesy.peershare.iface;

import android.os.Parcel;
import android.os.Parcelable;

public class Long implements Parcelable {

	private long value;
	
	public Long(long value) {
		this.value = value;
	}
	
	public Long(Long obj) {
		this.value = obj.getValue();
	}
	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub
		dest.writeLong(this.value);
	}
	
    public static final Parcelable.Creator<Long> CREATOR = new Parcelable.Creator<Long>() {
    	public Long createFromParcel(Parcel in) {
    		return new Long(in);
    	}

    	public Long[] newArray(int size) {
    		return new Long[size];
    	}
    };
    
    private Long(Parcel in) {
    	this.value = in.readLong();
    }

	public long getValue() {
		return this.value;
	}
}
