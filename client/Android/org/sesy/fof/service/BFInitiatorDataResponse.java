package org.sesy.fof.service;

import java.io.Serializable;
import java.util.BitSet;

import android.os.Parcel;
import android.os.Parcelable;

public class BFInitiatorDataResponse extends InitiatorDataResponse implements
		Parcelable, Serializable {

	private BitSet bf_container;
	private int bf_container_size;
	private int bf_container_number_of_elements;
	private int hash_number;
	
	private int status;
	
	static final long serialVersionUID = 4209360273818925922L;
	
	public BFInitiatorDataResponse(BitSet bf, int bf_size, int bf_elements, int hash, int stat, int response_type, 
			String localid, String remoteid, byte sessionid) {
		super(response_type,localid,remoteid,sessionid);
		this.bf_container = bf;
		this.bf_container_size = bf_size;
		this.bf_container_number_of_elements = bf_elements;
		this.hash_number = hash;
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
	
	/*private static void writeBitSet(Parcel dest, BitSet set) {
        int nextSetBit = -1;

        dest.writeInt(set.cardinality());

        while ((nextSetBit = set.nextSetBit(nextSetBit + 1)) != -1)
            dest.writeInt(nextSetBit);
	}*/
	
    /*private static BitSet readBitSet(Parcel src) {
        int cardinality = src.readInt();

        BitSet set = new BitSet();
        for (int i = 0; i < cardinality; i++)
            set.set(src.readInt());

        return set;
    }*/
    
	public static final Parcelable.Creator<BFInitiatorDataResponse> CREATOR= new Parcelable.Creator<BFInitiatorDataResponse>() {

		@Override
		public BFInitiatorDataResponse createFromParcel(Parcel source) {
			return new BFInitiatorDataResponse(source);
		}

		@Override
		public BFInitiatorDataResponse[] newArray(int size) {
			return new BFInitiatorDataResponse[size];
		}
	};
	
	protected BFInitiatorDataResponse(Parcel source) {
		super(source);
		/*this.status = source.readInt();
		this.bf_container_size = source.readInt();
		this.bf_container = BFRequestorDataResponse.readBitSet(source);
		this.bf_container_number_of_elements = source.readInt();*/
		BFInitiatorDataResponse tmp = (BFInitiatorDataResponse)source.readSerializable();
		this.status = tmp.getBloomFilterProtocolStatus();
		this.bf_container_size = tmp.getBloomFilterContainerSize();
		this.bf_container = tmp.getBloomFilterContainer();
		this.bf_container_number_of_elements = tmp.getBloomFilterContainerNumberOfElements();
		this.hash_number = tmp.getBloomFilterHashFunctionsAmount();
	}
	
	public BitSet getBloomFilterContainer() {
		return this.bf_container;
	}
	
	public int getBloomFilterContainerSize() {
		return this.bf_container_size;
	}
	
	public int getBloomFilterContainerNumberOfElements() {
		return this.bf_container_number_of_elements;
	}
	
	public int getBloomFilterHashFunctionsAmount() {
		return this.hash_number;
	}
	
	public int getBloomFilterProtocolStatus() {
		return this.status;
	}
	
	@Override
	public String toString() {
		return "filter size=" + this.bf_container_size + " #elements=" + bf_container_number_of_elements + " filter=" + bf_container;
	}
}
