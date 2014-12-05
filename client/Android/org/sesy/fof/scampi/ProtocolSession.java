package org.sesy.fof.scampi;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.sesy.fof.service.KeyDataContainer;
import org.sesy.peershare.iface.SocialInfo;

import com.skjegstad.utils.BloomFilter;

public class ProtocolSession {
	private KeyDataContainer remote_key;
	private Map<ArrayList<SocialInfo>,BigInteger> local_input_data;
	private BloomFilter<BigInteger> bf;
	private Map<ArrayList<SocialInfo>,BigInteger> provisional_results;
	private List<ArrayList<SocialInfo> > final_results;
	private boolean direct;
	private SecretKey rrand;
	private int role;
	private State state;
	private List<BigInteger> my_input;
	private Timestamp start_time; // added
	private Timestamp end_time;
	private String remote_id; // added
	private double computation_time;
	private double communication_time;
	private int input_size; // added
	private int recv_transfer_size; // added
	private int send_transfer_size; // added
	
	private FoFUnit.TimeoutTask timeout;
	
	public static final int INITIATOR = 1;
	public static final int RESPONDER = 2;
	
	public enum State {
		STARTRESPONDER,
		STARTINITIATOR,
		PROCESS,
		PROCESSCONTAINER
	}
	
	public ProtocolSession(int r, String id) {
		this.start_time = new Timestamp(System.currentTimeMillis());
		this.remote_id = id;
		this.role = r;
		this.remote_key = null;
		this.state = State.STARTINITIATOR;
		this.direct = false;
		this.communication_time = 0;
		this.computation_time = 0;
	}
	
	public ProtocolSession(int r, KeyDataContainer rk, Map<ArrayList<SocialInfo>,BigInteger> data,List<BigInteger> my, String id, int sent, int recv) {
		this.start_time = new Timestamp(System.currentTimeMillis());
		this.remote_id = id;
		this.role = r;
		this.remote_key = rk;
		this.local_input_data = data;
		this.my_input = my;
		this.state = State.PROCESS;
		this.direct = false;
		this.send_transfer_size = sent;
		this.recv_transfer_size = recv;
		this.communication_time = 0;
		this.computation_time = 0;
	}
	
	public void setKeyDataContainer(KeyDataContainer ctr) {
		this.remote_key = ctr;
	}
	
	public KeyDataContainer getKeyDataContainer() {
		return this.remote_key;
	}
	
	public void setProtocolLocalInputData(Map<ArrayList<SocialInfo>,BigInteger> input) {
		this.local_input_data = input;
	}
	
	public void setProtocolMyInputData(List<BigInteger> my) {
		this.my_input = my;
	}
	
	public Map<ArrayList<SocialInfo>,BigInteger> getProtocolLocalInputData() {
		return this.local_input_data;
	}
	
	public List<BigInteger> getProtocolMyInputData() {
		return this.my_input;
	}
	
	public void setProvisionalResults(Map<ArrayList<SocialInfo>,BigInteger> res) {
		this.provisional_results = res;
	}
	
	public Map<ArrayList<SocialInfo>,BigInteger> getProvisionalResults() {
		return this.provisional_results;
	}
	
	public void setBloomFilter(BloomFilter<BigInteger> filter) {
		this.bf = filter;
	}
	
	public BloomFilter<BigInteger> getBloomFilter() {
		return this.bf;
	}
	
	public void setRRand(SecretKey r) {
		this.rrand = r;
	}
	
	public SecretKey getRRand() {
		return this.rrand;
	}
	
	public void setState(ProtocolSession.State s) {
		this.state = s;
	}
	
	public ProtocolSession.State getState() {
		return this.state;
	}
	
	public void setFinalResults(List<ArrayList<SocialInfo> > res, boolean d) {
		this.final_results = res;
		this.direct = d;
		this.end_time = new Timestamp(System.currentTimeMillis());
	}
	
	public List<ArrayList<SocialInfo> > getFinalResults() {
		return this.final_results;
	}
	
	public boolean directFriendship() {
		return this.direct;
	}
	
	public void updateRecvBytes(int bytes) {
		this.recv_transfer_size += bytes;
	}
	
	public int getRecvBytes() {
		return this.recv_transfer_size;
	}
	
	public void updateSentBytes(int bytes) {
		this.send_transfer_size += bytes;
	}
	
	public int getSentBytes() {
		return this.send_transfer_size;
	}
	
	public void updateInputSize(int size) {
		this.input_size = size;
	}
	
	public int getInputSize() {
		return this.input_size;
	}
	
	public void updateComputationTime(double interval) {
		this.computation_time += interval;
	}
	
	public double getComputationTime() {
		return this.computation_time;
	}
	
	public void updateCommunicationTime(double interval) {
		this.communication_time += interval;
	}
	
	public double getCommunicationTime() {
		return this.communication_time;
	}
	
	public Timestamp getStartTime() {
		return this.start_time;
	}
	
	public Timestamp getEndTime() {
		return this.end_time;
	}
	
	public String getRemoteID() {
		return this.remote_id;
	}
	
	public int getProtocolRole() {
		return this.role;
	}
	
	public FoFUnit.TimeoutTask getTimeoutHandler() {
		return this.timeout;
	}
	
	public void cancelTimeout() {
		if(this.timeout != null)
			this.timeout.cancel();
	}
	
	public void setTimeout(FoFUnit.TimeoutTask task) {
		this.timeout = task;
	}
}
