package org.sesy.fof.scampi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSession;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.sesy.fof.service.BFInitiatorDataResponse;
import org.sesy.fof.service.BFNonceContainer;
import org.sesy.fof.service.BFNonceInitiatorContainer;
import org.sesy.fof.service.BFProtocolResult;
import org.sesy.fof.service.BFResponderDataResponse;
import org.sesy.fof.service.FoFAlgorithmType;
import org.sesy.fof.service.FoFObject;
import org.sesy.fof.service.InitialInitiatorContainer;
import org.sesy.fof.service.InitiatorDataResponse;
import org.sesy.fof.service.KeyDataContainer;
import org.sesy.fof.service.ResponderDataResponse;
import org.sesy.fof.service.ResultContainer;
import org.sesy.peershare.core.PeerShareService;
import org.sesy.peershare.iface.AppData;
import org.sesy.peershare.iface.SocialInfo;
import org.sesy.socialauth.SocialNetworkAccount;
import org.sesy.tetheringapp.Logger;
import org.sesy.tetheringapp.R;
import org.sesy.tetheringapp.TetheringActivity;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.skjegstad.utils.BloomFilter;

import fi.tkk.netlab.net.Util;


public class FoFUnit {
	private Context context;
	private KeyPair key_pair;
	
	private List<SocialNetworkAccount> social_identities;
	private List<SocialInfo> social_ids;
	private Map<Integer,String> anonymised_ids;
	
	private String scampiID;
	private Map<ArrayList<SocialInfo>,String> my_friends;
	private Map<String,ProtocolSession> sessions;
	private PeerShareServiceListener listener;
	private PeerShareService peerShare;
	private PrintWriter log;
	
	private Timer timeout_handler;
	private FoFProtocolTimeoutCallback timeout_callback;
	private FoFTokenRefreshCompleted token_refresh_callback;
	
	private SecretKey ckey;

	private static final double false_positive_probability = 0.0001;
	private static final long bearer_capability_lifetime = 7 * 24 * 3600 * 1000; // 7 days

	private static final String TAG = "FoFUnit";
	private static final String PUBLIC_KEY_FILE = "public_key";
	private static final String PRIVATE_KEY_FILE = "private_key";
	private static final String KEY_ALGORITHM = "EC";
	private static final String KEY_AGREEMENT = "ECDH";
	private static final String SECRET_KEY = "AES";
	private static final String ECDH_CURVE = "prime192v1";
	private static final String MAC_ALGORITHM = "HmacSHA1";
	private static final String KDF_FUNCTION = "SHA-1";
	private static final String PEER_CERTIFICATES = "peer-certificates";
	private static final long TIMEOUT = 60000;
	private static final String NONCE_DESCRIPTION = "TetheringApp capability";
	
	private static final int tooltipShowDuration = 1000;
	
	public FoFUnit(Context ctx, PeerShareService ps, FoFProtocolTimeoutCallback fof_timeout_callback,
			FoFTokenRefreshCompleted refresh_callback,List<SocialNetworkAccount> accounts) {
		//Log.d(TAG,"FoFUnit constructor");
		this.context = ctx;
		this.peerShare = ps;
		this.social_identities = accounts;
		this.anonymised_ids = new HashMap<Integer, String>();
		this.social_ids = new ArrayList<SocialInfo>();
		for(SocialNetworkAccount a : this.social_identities) {
			//Log.d(TAG,"Adding my social identity: " + a.getSocialNetworkName() + " " + a.getUsername());
			this.social_ids.add(new SocialInfo(a.getUserID(), a.getUsername(), a.getSocialNetworkID()));
		}
		
		this.timeout_callback = fof_timeout_callback;
		this.token_refresh_callback = refresh_callback;
		this.ckey = null;

		this.timeout_handler = new Timer();
		this.sessions = new HashMap<String, ProtocolSession>();
		this.my_friends = new HashMap<ArrayList<SocialInfo>, String>();
		this.listener = new PeerShareServiceListener();
		IntentFilter filter = new IntentFilter();
		filter.addAction("org.sesy.peershare");
		ctx.registerReceiver(listener, filter);

		fetchFoFLogKey();
		
		File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"ccs_app");
		if (!file.exists()) {
			Logger.d(TAG,"Creating directory for the new log file",this);
			file.mkdirs();
		}
		Logger.d(TAG, "File location:" + file.getAbsolutePath(),this);
	    
		try {
			File logfile = new File(file.getAbsolutePath() + "/ccs_fof.log");
			if(!logfile.exists()) logfile.createNewFile();
		
			FileWriter logwriter = new FileWriter(logfile.getAbsolutePath(), false);
			log = new PrintWriter(logwriter);
			Logger.d(TAG,"Buffered log: " + log,this);
		} catch(IOException e) {
			Logger.e(TAG,"Log file creation error: " + e.getMessage(),this);
		}
		
 	    if(log!=null) {
 	        log.println("\n\n\nCCS app FoF unit starts at: " + DateFormat.getDateTimeInstance().format(System.currentTimeMillis()));
	        log.flush();
	    }
		
		this.key_pair = loadKeyPair();
		if(this.key_pair == null) {
			Logger.d(TAG,"Key pair not found. Generating a new pair",this);
			if(log!=null) {
				log.println("Key pair not found. Generating a new pair");
				log.flush();
			}
			try {
				KeyPairGenerator key_generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
				key_generator.initialize(new ECGenParameterSpec(ECDH_CURVE));
				this.key_pair = key_generator.generateKeyPair();
				saveKeyPair();
			} catch(InvalidAlgorithmParameterException e) {
				Logger.e(TAG,"Invalid EC algorithm parameter exception for key pair generation: " + e.getMessage(),this);
				if(log!=null) {
					log.println("Invalid EC algorithm parameter exception for key pair generation: " + e.getMessage());
					log.flush();
				}
			} catch(NoSuchAlgorithmException e) {
				Logger.e(TAG,"No such EC algorithm exception for key pair generation: " + e.getMessage(),this);
				if(log!=null) {
					log.println("No such EC algorithm exception for key pair generation: " + e.getMessage());
					log.flush();
				}
			}
		}
		
		try {
			MessageDigest digest = MessageDigest.getInstance(KDF_FUNCTION);
			byte[] public_key_hash = digest.digest(this.key_pair.getPublic().getEncoded());
			scampiID = Util.toHexString(public_key_hash);
			Logger.d(TAG,"Assigning SCAMPI ID=" + scampiID + " ckey=" + ckey,this);
			if(ckey!=null) {
				registerUser();
			}
		} catch(NoSuchAlgorithmException e) {
			Logger.e(TAG,"No such algorithm exception for host ID generation: " + e.getMessage(),this);
			if(log!=null) {
				log.println("No such algorithm exception for host ID generation: " + e.getMessage());
				log.flush();
			}			
		}
		
		int status = ps.getServiceStatus();
		Log.d(TAG,"PeerShare service status: " + status);
		
		if(status == 0) {
			if(!isMyBearerCapabilityUploaded()) {
				//Log.d(TAG,"Service is already ready to be used. Generating my data");
				generateBearerCapability(false, -1);
			}
			if(this.my_friends==null || this.my_friends.isEmpty()) {
				//Log.d(TAG,"Calling updateMyBearerCapabilityData in constructor");
				updateMyBearerCapabilityData(true,false);
			}
		}
		
	}
	
	public void assignScampiID(String id) {
		this.scampiID = id;
		Logger.d(TAG,"Assigning SCAMPI ID=" + id + " ckey=" + ckey,this);
		if(ckey!=null) {
			registerUser();
		}
	}
	
	public void addNewSocialNetworkAccount(List<SocialNetworkAccount> a) {
		this.social_identities = a;
		for(SocialNetworkAccount i : this.social_identities) {
			SocialInfo info = new SocialInfo(i.getUserID(), i.getUsername(), i.getSocialNetworkID());
			if(!this.social_ids.contains(info)) {
				//Log.d(TAG,"Adding my social identity: " + i.getSocialNetworkName() + " " + i.getUsername());
				this.social_ids.add(info);
			}
		}
	}
	
	public String getHostID() {
		return this.scampiID;
	}

	public InitialInitiatorContainer startFoFSession(String hostid) {
		if(!sessions.containsKey(hostid)) {
			long start = System.currentTimeMillis();
			if(log!=null) {
				log.println("Starting new FoF session with host: " + hostid);
				log.flush();
			}
			ProtocolSession session = new ProtocolSession(ProtocolSession.INITIATOR,hostid);
			PublicKey public_key = this.key_pair.getPublic();
			KeyDataContainer key_ctr = new KeyDataContainer(public_key.getEncoded(),public_key.getFormat(),public_key.getAlgorithm());
			
			Random rnd = new Random();
			int session_id = rnd.nextInt(256);
			
			InitialInitiatorContainer initial_ctr = new InitialInitiatorContainer(key_ctr, false, true, 
					this.my_friends.size(), this.scampiID, (byte)session_id);
			int bytes_sent = 0;
			try {
				bytes_sent = Memory.sizeOf(initial_ctr);
			} catch(IOException e) {
				//Log.e(TAG, "IO exception on obtaining amount of sent bytes in startFoFSession()");
			}
			session.updateSentBytes(bytes_sent);
			
			String sessionID = hostid + String.valueOf((byte)session_id);
			sessions.put(sessionID, session);
			session.updateComputationTime(System.currentTimeMillis()-start);
			TimeoutTask task = new TimeoutTask(sessionID, hostid);
			session.setTimeout(task);
			timeout_handler.schedule(task, FoFUnit.TIMEOUT);
			return initial_ctr;
		} else {
			//Log.d(TAG, "FoF protocol already run with the node");
			if(log!=null) {
				log.println("FoF protocol already run with the node");
				log.flush();
			}
			return null;
		}
	}
	
	public int endFoFSession(String hostid) {
		if(sessions.containsKey(hostid)) {
			sessions.remove(hostid);
			if(log!=null) {
				log.println("FoF session with host " + hostid + " finished");
				log.flush();
			}
			return 0;
		} else
			return -1;
	}
	
	public int endFoFSession() {
		for(String s : sessions.keySet()) {
			sessions.remove(s);
		}
		return 0;
	}
	
	public ResponderDataResponse rejectFoFSession(String hostid, InitialInitiatorContainer ctr) {
		return new BFResponderDataResponse(null, null, false,this.scampiID,hostid,ctr.getSessionID());
	}
	
	public ResultContainer handleFoFMessage(Object msg, String remote_id) {
		
		ResultContainer result = null;
		if(!(msg instanceof FoFObject)) {
			//Log.d(TAG,"Wrong FoF message. Returning null response");
			return result;
		}
		FoFObject o = (FoFObject)msg;
		
		String sessionIdentifier = remote_id + String.valueOf((byte)o.getSessionID());
		//Log.d(TAG,"Session identifier: " + remote_id + " " + o.getSessionID());
		ProtocolSession session = this.sessions.get(sessionIdentifier);
		Log.d(TAG,"Handling FoF message for remote ID: " + session);
		Log.d(TAG,"Number of active sessions: " + this.sessions.size());
		for(String id : this.sessions.keySet()) {
			//Log.d(TAG,"id: " + id);
		}
		if(log!=null) {
			log.println("Handling ongoing FoF session with host " + remote_id);
			log.flush();
		}
		if(session != null) {
			/* On going session */
			if(msg instanceof BFResponderDataResponse) {
				Logger.d(TAG,"Calling startInitiator()",this);
				Log.d(TAG,"Calling startInitiator()");
				if(log!=null) {
					log.println("Calling startInitiator()");
					log.flush();
				}
				BFResponderDataResponse ctr = (BFResponderDataResponse)msg;
				if(ctr.getRequestStatus() == false) {
					Log.d(TAG,"FoF session is terminated, as the other party doesn't want to run the protocol");
					if(log!=null) {
						log.println("FoF session is terminated, as the other party doesn't want to run the protocol");
						log.flush();
					}
					result = new ResultContainer(ResultContainer.STATUS_REJECTED, null,sessionIdentifier);
				} else {
					BFInitiatorDataResponse resp = startInitiator(session, ctr);
					result = new ResultContainer(ResultContainer.STATUS_WAIT,resp,sessionIdentifier);
				}
			} else if(msg instanceof BFInitiatorDataResponse) {
				Logger.d(TAG,"Calling process()",this);
				Log.d(TAG,"Calling process()");
				if(log!=null) {
					log.println("Calling process()");
					log.flush();
				}
				BFInitiatorDataResponse ctr = (BFInitiatorDataResponse)msg;
				result = process(session, ctr, sessionIdentifier);
			} else if(msg instanceof BFNonceContainer) {
				Logger.d(TAG,"Calling processInitiatorContainer()",this);
				Log.d(TAG,"Calling processInitiatorContainer()");
				if(log!=null) {
					log.println("Calling processInitiatorContainer()");
					log.flush();
				}
				BFNonceContainer ctr = (BFNonceContainer)msg;
				result = processInitiatorContainer(session, ctr,sessionIdentifier);
			} else if(msg instanceof BFNonceInitiatorContainer) {
				Logger.d(TAG,"Calling processResponderContainer()",this);
				Log.d(TAG,"Calling processResponderContainer()");
				if(log!=null) {
					log.println("Calling processResponderContainer()");
					log.flush();
				}				
				BFNonceInitiatorContainer ctr = (BFNonceInitiatorContainer)msg;
				result = processResponderContainer(session, ctr, sessionIdentifier);
			} else {
				//Log.d(TAG,"Received object type: " + msg.getClass().getName());
			}
		} else if(msg instanceof InitialInitiatorContainer) {
			/* We're responders to the new session */
			Logger.d(TAG, "Calling startResponder()",this);
			Log.d(TAG,"Calling startResponder()");
			if(log!=null) {
				log.println("Session started by remote host. Calling startResponder()");
				log.flush();
			}
			InitialInitiatorContainer ctr = (InitialInitiatorContainer)msg;
			ResponderDataResponse resp = startResponder(remote_id, ctr);
			result = new ResultContainer(ResultContainer.STATUS_WAIT, resp, sessionIdentifier);
		} else {
			if(log!=null) {
				log.println("Ignoring received message");
				log.flush();
			}
		}
		return result;
	}
	
	public BFProtocolResult getResult(byte sessionid, String hostid) {
		String sessionIdentifier = hostid + String.valueOf(sessionid);
		ProtocolSession session = this.sessions.get(sessionIdentifier);
		BFProtocolResult result = null;
		if(log!=null) {
			log.println("Getting results for session with host " + hostid);
			log.flush();
		}
		if(session!=null) {
			List<ArrayList<SocialInfo> > common_friends = session.getFinalResults();
			boolean directFriends = session.directFriendship();
			if(log!=null) {
				for(ArrayList<SocialInfo> info : common_friends) {
					log.println(info.get(0).getSocialName() + " is a common friend");
					//Log.d(TAG,info.get(0).getSocialName() + " is a common friend");
				}
				log.flush();
			}
			
			SecretKey secret_key = null;
			byte[] public_encoded_key = session.getKeyDataContainer().getEncodedKey();
			try {
				PublicKey public_key = KeyFactory.getInstance(FoFUnit.KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(public_encoded_key));
				PrivateKey private_key = this.key_pair.getPrivate();
			
				KeyAgreement ecdh = KeyAgreement.getInstance(FoFUnit.KEY_AGREEMENT);
				ecdh.init(private_key);
				ecdh.doPhase(public_key, true);
				secret_key = ecdh.generateSecret(FoFUnit.SECRET_KEY);
			} catch(InvalidKeySpecException e) {
	    		//Log.e(TAG,"Invalid key spec exception on DH: " + e.getMessage());
	    		e.printStackTrace();
	    	} catch(NoSuchAlgorithmException e) {
	    		//Log.e(TAG,"No such algorithm exception on DH: " + e.getMessage());
	    		e.printStackTrace();
	    	} catch(InvalidKeyException e) {
	    		//Log.e(TAG,"Invalid key exception on DH: " + e.getMessage());
	    		e.printStackTrace();
	    	}
			
			
			result = new BFProtocolResult(getHostID(),session.getRemoteID(),sessionid, common_friends, secret_key,directFriends);
		}
		
		return result;
	}
	
	public void startTokenRefresh() {
		List<org.sesy.peershare.iface.Long> ids = peerShare.getMyData(AppData.DataType.CROWDAPP);
		if(!ids.isEmpty()) {
			long id = ids.get(0).getValue();
			AppData data = peerShare.getMyDataDetail(id);
			try {
				peerShare.updateDataForceDownload(id, data);
			} catch(RemoteException e) {
				//Log.e(TAG,"Remote exception on refreshing token invoked on demand");
			}
		} else {
			//Log.e(TAG,"No token available locally. Not refreshing");
		}
	}
	
	public void renewOwnToken() {
		updateMyBearerCapabilityData(true,true);
	}
	
	public void updateSharedTokens() {
		updateMyBearerCapabilityData(false,false);
	}

	private boolean isNetworkAvailable() {
	    ConnectivityManager connectivityManager 
	          = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}
	
	protected ResponderDataResponse startResponder(String hostid, InitialInitiatorContainer ctr){

		int bytes_recv = 0, bytes_sent = 0;
		long start = System.currentTimeMillis();
		try {
			bytes_recv += Memory.sizeOf(ctr);
		} catch(IOException e) {
			//Log.e(TAG, "IOException on gettin received message size");
		}
		KeyDataContainer initiator_public_key_ctr = ctr.getPublicKeyDataContainer();               
		FoFAlgorithmType type = new FoFAlgorithmType(FoFAlgorithmType.BFPSI, ctr.getNumberFriends());
		byte sessionID = ctr.getSessionID();

		byte[] initiator_pk = initiator_public_key_ctr.getEncodedKey();
		byte[] responder_pk = this.key_pair.getPublic().getEncoded();
		String initiator_public_key = NonceGenerator.byteArrayToHexString(initiator_pk);
		String responder_public_key = NonceGenerator.byteArrayToHexString(responder_pk);

		Map<ArrayList<SocialInfo>,BigInteger> responder_input_data = new HashMap<ArrayList<SocialInfo>,BigInteger>();
		List<BigInteger> my_input_data = new ArrayList<BigInteger>();
		
		//Log.d(TAG,"Total number of my friends: " + this.my_friends.size());
		for(ArrayList<SocialInfo> friend: this.my_friends.keySet()) {
			BigInteger temp = new BigInteger(this.my_friends.get(friend) + initiator_public_key + responder_public_key,16);
			responder_input_data.put(friend, temp);
			
			boolean my_item = true;
			for(SocialInfo s : friend) {
				if(!this.social_ids.contains(s)) {
					my_item = false;
					break;
				}
			}
			if(my_item) {
				//Log.d(TAG,"Adding my own input item");
				my_input_data.add(temp);
			}
		}	

		int input_size = Math.max(type.getFriendsNumber(), this.my_friends.keySet().size());
		PublicKey key = this.key_pair.getPublic();
		KeyDataContainer responder_key = new KeyDataContainer(key.getEncoded(),key.getFormat(),key.getAlgorithm());		
		FoFAlgorithmType responder_type = new FoFAlgorithmType(type.getFriendshipDepth(),FoFAlgorithmType.BFPSI, input_size);
		BFResponderDataResponse tmp = new BFResponderDataResponse(responder_key, responder_type,true,this.scampiID,hostid,sessionID);
		try {
			bytes_sent += Memory.sizeOf(tmp);
		} catch(IOException e) {
			//Log.e(TAG, "IOException on gettin received message size");
		}
		
		ProtocolSession session = new ProtocolSession(ProtocolSession.RESPONDER,initiator_public_key_ctr,responder_input_data,
				my_input_data,hostid,bytes_sent,bytes_recv);
		session.updateInputSize(input_size);
		session.updateComputationTime(System.currentTimeMillis()-start);
		
		String sessionIdentifier = hostid + String.valueOf(sessionID);
		TimeoutTask task = new TimeoutTask(sessionIdentifier, hostid);
		session.setTimeout(task);
		
		sessions.put(sessionIdentifier, session);
		timeout_handler.schedule(task, FoFUnit.TIMEOUT);
		return tmp;
	}

	protected BFInitiatorDataResponse startInitiator(ProtocolSession session, BFResponderDataResponse responderParams) {
		BFInitiatorDataResponse response = null;
		KeyDataContainer remote_public_key = responderParams.getPublicKey();
		FoFAlgorithmType type = responderParams.getAlgorithmType();

		if(session!=null) {
			long start = System.currentTimeMillis();
			int bytes_sent = 0, bytes_recv = 0;
			try {
				bytes_recv = Memory.sizeOf(responderParams);
			} catch(IOException e) {
				//Log.e(TAG,"IO exception on obtaining amount of read bytes in startInitiator");
			}
			BloomFilter<BigInteger> initiator_filter = new BloomFilter<BigInteger>(false_positive_probability, type.getFriendsNumber());
			session.setKeyDataContainer(remote_public_key);
			byte[] responder_pk = remote_public_key.getEncodedKey();
			byte[] initiator_pk = this.key_pair.getPublic().getEncoded();
			String initiator_public_key = NonceGenerator.byteArrayToHexString(initiator_pk);
			String responder_public_key = NonceGenerator.byteArrayToHexString(responder_pk);

			Map<ArrayList<SocialInfo>,BigInteger> initiator_input_data = new HashMap<ArrayList<SocialInfo>,BigInteger>();
			List<BigInteger> my_input = new ArrayList<BigInteger>();
			
			//for(SocialInfo s : this.social_ids)
				//Log.d(TAG,"My registered identity: " + s.toString());
			
			for(ArrayList<SocialInfo> friend: this.my_friends.keySet()) {
				BigInteger temp = new BigInteger(this.my_friends.get(friend) + initiator_public_key + responder_public_key,16);
				initiator_input_data.put(friend, temp);
				
				boolean my_item = true;
				for(SocialInfo s : friend) {
					//Log.d(TAG,"Checking: " + s.toString());
					if(!this.social_ids.contains(s)) {
						//Log.d(TAG,"Missing my identity for: " + s.toString());
						my_item = false;
						break;
					}
				}
				if(my_item) {
					//Log.d(TAG,"Adding my own input item");
					my_input.add(temp);
				}
			}
			
			session.setProtocolLocalInputData(initiator_input_data);
			session.setProtocolMyInputData(my_input);
			session.updateInputSize(type.getFriendsNumber());


			for(ArrayList<SocialInfo> item : initiator_input_data.keySet()) {
				initiator_filter.add(initiator_input_data.get(item));
			}
			/*Log.d(TAG,"Sending Bloom filter: " + initiator_filter.getBitSet() + " length: " + initiator_filter.size()
					+ " items: " + initiator_filter.count());*/
			session.setBloomFilter(initiator_filter);
			
			BitSet bf_container = initiator_filter.getBitSet();
			int bf_size = initiator_filter.size();
			int numberOfElements = initiator_filter.getExpectedNumberOfElements();
			int status = InitiatorDataResponse.STATUS_DONE;
			int hash_number = initiator_filter.getK();
			response = new BFInitiatorDataResponse(bf_container, bf_size, numberOfElements, hash_number, status, 
					InitiatorDataResponse.ResponseType.PSI_BFPSI,this.scampiID,session.getRemoteID(),responderParams.getSessionID());
			try {
				bytes_sent = Memory.sizeOf(response);
			} catch(IOException e) {
				//Log.e(TAG,"IO exception on obtaining amount of sent bytes in startInitiator");
			}
			session.setState(ProtocolSession.State.PROCESSCONTAINER);
			session.updateRecvBytes(bytes_recv);
			session.updateSentBytes(bytes_sent);
			session.updateComputationTime(System.currentTimeMillis()-start);
		}

		return response;
	}

	protected ResultContainer process(ProtocolSession session, BFInitiatorDataResponse rdr, String id) {
		ResultContainer response = null;

		if(session!=null) {
			long start = System.currentTimeMillis();
			int bytes_recv = 0, bytes_sent = 0;
			try {
				bytes_recv = Memory.sizeOf(rdr);
			} catch(IOException e) {
				//Log.e(TAG, "IO exception on getting received message size in process()");
			}
			int size = rdr.getBloomFilterContainerSize();
			int elements = rdr.getBloomFilterContainerNumberOfElements();
			BitSet data = rdr.getBloomFilterContainer();
			BloomFilter<BigInteger> recv_filter = new BloomFilter<BigInteger>(FoFUnit.false_positive_probability, elements);
			recv_filter.setBloomFilterContent(elements, data);
			//Log.d(TAG,"process() call; filter size="+size+" #elements="+elements+" content="+data+" filter="+recv_filter);

			
			Map<ArrayList<SocialInfo>,BigInteger> responder_provisional_results = new HashMap<ArrayList<SocialInfo>,BigInteger>();

			try {
				KeyGenerator keygen = KeyGenerator.getInstance(MAC_ALGORITHM);
				SecretKey ckey = keygen.generateKey();
				SecretKey rrand = keygen.generateKey();
				session.setRRand(rrand);
				Mac mac = Mac.getInstance(keygen.getAlgorithm());
				mac.init(ckey);
				HashSet<String> cset = new HashSet<String>();
				Map<ArrayList<SocialInfo>,BigInteger> responder_input_data = session.getProtocolLocalInputData();
								
				//Log.d(TAG,"Responder input data size: " + responder_input_data.size());
				for(ArrayList<SocialInfo> key_name : responder_input_data.keySet()) {
					BigInteger nonce = responder_input_data.get(key_name);
					if(recv_filter.contains(nonce)) {
						//Log.d(TAG,key_name.get(0).toString() + " is provisionally a common friend");
						responder_provisional_results.put(key_name, nonce);
						byte[] digest = mac.doFinal(nonce.toByteArray());
						cset.add(Base64.encodeToString(digest,Base64.NO_WRAP));
					} else {
						//Log.d(TAG,key_name.get(0).toString() + " is not a common friend");
					}
				}
				session.setProvisionalResults(responder_provisional_results);

				BFNonceContainer mesg = new BFNonceContainer(cset,Base64.encodeToString(ckey.getEncoded(),Base64.NO_WRAP),
						Base64.encodeToString(rrand.getEncoded(),Base64.NO_WRAP),this.scampiID, session.getRemoteID(),rdr.getSessionID());
				try {
					bytes_sent = Memory.sizeOf(mesg);
				} catch(IOException e) {
					//Log.e(TAG, "IO exception on getting sent message size in process()");
				}
				
				// FIXME: no need to send more messages if there are no common friends
				response = new ResultContainer(ResultContainer.STATUS_WAIT,mesg,id);
				session.setState(ProtocolSession.State.PROCESSCONTAINER);
				session.updateRecvBytes(bytes_recv);
				session.updateSentBytes(bytes_sent);
				session.updateComputationTime(System.currentTimeMillis()-start);
			} catch(NoSuchAlgorithmException e) {
				// Log.e(TAG,"No such algorithm exception: " + e.getMessage());
				e.printStackTrace();
			} catch(InvalidKeyException e) {
				// Log.e(TAG,"InvalidKey exception: " + e.getMessage());
				e.printStackTrace();			
			}
		}
		return response;	
	}

	protected ResultContainer processInitiatorContainer(ProtocolSession session, BFNonceContainer msg, String id) {
		ResultContainer result = null;

		//int id = msg.getInitiatorSessionID();
		if(session!=null) {
			try {
				long start = System.currentTimeMillis();
				int bytes_sent = 0, bytes_recv = 0;
				try {
					bytes_recv = Memory.sizeOf(msg);
				} catch(IOException e) {
					//Log.e(TAG, "IO exception on obtaining amount of recv bytes in processInitiatorContainer()");
				}
				KeyGenerator keygen = KeyGenerator.getInstance(MAC_ALGORITHM);
				SecretKey irand = keygen.generateKey();
				byte[] ckey_raw = Base64.decode(msg.getCKey(),Base64.NO_WRAP);
				byte[] rrand_raw = Base64.decode(msg.getRRand(),Base64.NO_WRAP);
				SecretKey ckey = new SecretKeySpec(ckey_raw, 0, ckey_raw.length, MAC_ALGORITHM);
				Mac mac_c = Mac.getInstance(keygen.getAlgorithm());
				Mac mac_r = Mac.getInstance(keygen.getAlgorithm());
				mac_c.init(ckey);
				MessageDigest kdf = MessageDigest.getInstance(KDF_FUNCTION);
				byte[] irand_raw = irand.getEncoded();
				byte[] kdf_input = new byte[irand_raw.length + rrand_raw.length];
				System.arraycopy(irand_raw, 0, kdf_input, 0, irand_raw.length);
				System.arraycopy(rrand_raw, 0, kdf_input, irand_raw.length, rrand_raw.length);
				byte[] rkey = kdf.digest(kdf_input);
				SecretKey Rkey = new SecretKeySpec(rkey, 0, rkey.length, MAC_ALGORITHM);
				mac_r.init(Rkey);

				ArrayList<ArrayList<SocialInfo> > initiator_results = new ArrayList<ArrayList<SocialInfo> >();

				HashSet<String> cset = msg.getCSet();
				HashSet<String> rset = new HashSet<String>();

				Map<ArrayList<SocialInfo>,BigInteger> initiator_input_data = session.getProtocolLocalInputData();
				List<BigInteger> my_input = session.getProtocolMyInputData();
				boolean directFriends = false;
				//Log.d(TAG,"Initiator input data size: " + initiator_input_data.size());
				//Log.d(TAG,"CSet size: " + cset.size());
				//Log.d(TAG,"My input: " + my_input.get(0).toString(16));
				for(ArrayList<SocialInfo> key_name : initiator_input_data.keySet()) {
					BigInteger value = initiator_input_data.get(key_name);
					byte[] nonce = value.toByteArray();
					byte[] hash = mac_c.doFinal(nonce);
					if(cset.contains(Base64.encodeToString(hash,Base64.NO_WRAP))) {
						//Log.d(TAG,"Comparing: " + value.toString(16));
						if(!my_input.contains(value)) {
							initiator_results.add(key_name);
							//Log.d(TAG,key_name.get(0).toString() + " is a common friend");
						} else {
							directFriends = true;
							//Log.d(TAG,"Direct friend: " + key_name.get(0).toString());
						}
						byte[] digest = mac_r.doFinal(nonce);
						rset.add(Base64.encodeToString(digest,Base64.NO_WRAP));
					}
				}
				session.setFinalResults(initiator_results,directFriends);
				//Log.d(TAG,"Total number of common friends: " + initiator_results.size());
				BFNonceInitiatorContainer nonce_container = new BFNonceInitiatorContainer(rset, Base64.encodeToString(irand_raw,Base64.NO_WRAP),
						this.scampiID,session.getRemoteID(),msg.getSessionID());
				try {
					bytes_sent = Memory.sizeOf(nonce_container);
				} catch(IOException e) {
					//Log.e(TAG, "IO exception on obtaining amount of sent bytes in processInitiatorContainer()");
				}
				session.updateSentBytes(bytes_sent);
				session.updateRecvBytes(bytes_recv);
				session.updateComputationTime(System.currentTimeMillis()-start);
				session.cancelTimeout();
				logFoFResult(session);
				this.sessions.remove(session);
				result = new ResultContainer(ResultContainer.STATUS_DONE, nonce_container, id);
			} catch(NoSuchAlgorithmException e) {
				// Log.e(TAG,"No such algorithm exception: " + e.getMessage());
				e.printStackTrace();
			} catch(InvalidKeyException e) {
				// Log.e(TAG,"InvalidKey exception: " + e.getMessage());
				e.printStackTrace();			
			}
		}
		return result;
	}

	protected ResultContainer processResponderContainer(ProtocolSession session, BFNonceInitiatorContainer msg, String id) {
		ResultContainer result = null;
		//int id = msg.getResponderSessionID();

		if(session!=null) {
			try {
				long start = System.currentTimeMillis();
				int bytes_recv = 0;
				try {
					bytes_recv = Memory.sizeOf(msg);
				} catch(IOException e) {
					//Log.e(TAG,"IO exception on obtaining amount of received bytes in processResponderContainer()");
				}
				KeyGenerator keygen = KeyGenerator.getInstance(MAC_ALGORITHM);
				Mac mac_r = Mac.getInstance(keygen.getAlgorithm());
				MessageDigest kdf = MessageDigest.getInstance(KDF_FUNCTION);
				byte[] irand_raw = Base64.decode(msg.getIRand(),Base64.NO_WRAP);
				SecretKey rrand = session.getRRand();
				byte[] rrand_raw = rrand.getEncoded();
				byte[] kdf_input = new byte[irand_raw.length + rrand_raw.length];
				System.arraycopy(irand_raw, 0, kdf_input, 0, irand_raw.length);
				System.arraycopy(rrand_raw, 0, kdf_input, irand_raw.length, rrand_raw.length);
				byte[] rkey = kdf.digest(kdf_input);
				SecretKey Rkey = new SecretKeySpec(rkey, 0, rkey.length, "HmacSHA1");

				mac_r.init(Rkey);
				HashSet<String> rset = msg.getRSet();
				boolean directFriends = false;
				Map<ArrayList<SocialInfo>,BigInteger> responder_provisional_results = session.getProvisionalResults();
				List<BigInteger> my_input = session.getProtocolMyInputData();
				List<ArrayList<SocialInfo> > responder_final_results = new ArrayList<ArrayList<SocialInfo> >();
				for(ArrayList<SocialInfo> info : responder_provisional_results.keySet()) {
					BigInteger value = responder_provisional_results.get(info);
					byte[] nonce = value.toByteArray();
					byte[] hash = mac_r.doFinal(nonce);
					if(rset.contains(Base64.encodeToString(hash,Base64.NO_WRAP))) {
						if(!my_input.contains(value)) {
							responder_final_results.add(info);
							//Log.d(TAG,info.get(0).toString() + " is a common friend");
						} else {
							directFriends = true;
						}
					}
				}
				session.cancelTimeout();
				session.updateRecvBytes(bytes_recv);
				session.setFinalResults(responder_final_results,directFriends);
				session.updateComputationTime(System.currentTimeMillis()-start);
				logFoFResult(session);
				sessions.remove(session);
				result = new ResultContainer(ResultContainer.STATUS_DONE, null, id);			
			} catch(NoSuchAlgorithmException e) {
				// Log.e(TAG,"No such algorithm exception: " + e.getMessage());
				e.printStackTrace();
			} catch(InvalidKeyException e) {
				// Log.e(TAG,"InvalidKey exception: " + e.getMessage());
				e.printStackTrace();			
			}
		}
		return result;
	}

	protected KeyPair loadKeyPair() {
		Logger.d(TAG,"Loading key pair from file",this);
		try {
			// Read Public Key.
			File filePublicKey = new File(context.getFilesDir() + "/" + FoFUnit.PUBLIC_KEY_FILE);
			FileInputStream fis = context.openFileInput(FoFUnit.PUBLIC_KEY_FILE);
			byte[] encodedPublicKey = new byte[(int) filePublicKey.length()];
			fis.read(encodedPublicKey);
			fis.close();

			// Read Private Key.
			File filePrivateKey = new File(context.getFilesDir() + "/" + FoFUnit.PRIVATE_KEY_FILE);
			fis = context.openFileInput(PRIVATE_KEY_FILE);
			byte[] encodedPrivateKey = new byte[(int) filePrivateKey.length()];
			fis.read(encodedPrivateKey);
			fis.close();

			// Generate KeyPair.
			KeyFactory keyFactory = KeyFactory.getInstance("EC");
			X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedPublicKey);
			PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

			PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
			PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

			return new KeyPair(publicKey, privateKey);
		} catch(FileNotFoundException e) {
			Logger.e(TAG,"Files not found for key pair loading: " + e.getMessage(),this);
			return null;
		} catch(IOException e) {
			Logger.e(TAG,"IO exception for key pair loading: " + e.getMessage(),this);
			return null;
		} catch(InvalidKeySpecException e) {
			Logger.e(TAG,"Invalid key spec exception for key pair loading: " + e.getMessage(),this);
			return null;
		} catch(NoSuchAlgorithmException e) {
			Logger.e(TAG,"EC algorithm exception for key pair loading: " + e.getMessage(),this);
			return null;
		}
	}

	protected void saveKeyPair() {
		Logger.d(TAG,"Saving generated key pair to file",this);
		if(key_pair != null) {
			PrivateKey privateKey = key_pair.getPrivate();
			PublicKey publicKey = key_pair.getPublic();

			try {
				// Store Public Key.
				X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
				FileOutputStream fos = context.openFileOutput(FoFUnit.PUBLIC_KEY_FILE,Context.MODE_PRIVATE);
				fos.write(x509EncodedKeySpec.getEncoded());
				fos.close();

				// Store Private Key.
				PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
				fos = context.openFileOutput(FoFUnit.PRIVATE_KEY_FILE,Context.MODE_PRIVATE);
				fos.write(pkcs8EncodedKeySpec.getEncoded());
				fos.close();
			} catch(FileNotFoundException e) {
				Logger.e(TAG,"Cannot save the key pair. File not found: " + e.getMessage(),this);
			} catch(IOException e) {
				Logger.e(TAG,"Cannot save the key pair. IO exception: " + e.getMessage(),this);
			}
		} else {
			Logger.e(TAG,"Unable to store key pair. Key doesn't exist",this);
		}
	}
	
	private void setMyFriendsData(List<AppData> nonces) {
		Log.d(TAG,"setMyFriendsData() " + nonces.size());
		Map<ArrayList<SocialInfo>, String> temp = new HashMap<ArrayList<SocialInfo>, String>();
		for(AppData item : nonces) {
			ArrayList<SocialInfo> ids = (ArrayList<SocialInfo>)item.getDataOwnerInfo();
			temp.put(ids,item.getDataValue());
			//Log.d(TAG,"Identities:");
			//for(SocialInfo i : ids)
				//Log.d(TAG,"Identity: " + i.toString());
			//Log.d(TAG,"Inserting token: " + item.getDataValue());
		}
		
		if(this.my_friends!=null) {
			this.my_friends = temp;
			Log.i(TAG, "Successfully filled my friends data. Number of friends: " + this.my_friends.size());
		}
		
	}
	
	private void generateBearerCapability(boolean update, long id) {
		String nonce = NonceGenerator.newNonce();
		String algorithm = "plain";
		String description = FoFUnit.NONCE_DESCRIPTION;
		int specificity = AppData.Specificity.USER_SPECIFIC;
		int binding_type = AppData.BindingType.OWNER_ASSERTED;
		int sensitivity = AppData.DataSensitivity.PRIVATE;
		String policy = AppData.SharingPolicy.DEFAULT;
		Timestamp created = new Timestamp(System.currentTimeMillis());
		long createdTS = created.getTime();
		long expiryTS = createdTS + FoFUnit.bearer_capability_lifetime;
		Timestamp expires = new Timestamp(expiryTS);
		List<SocialInfo> ids = new ArrayList<SocialInfo>();
		for(SocialNetworkAccount i : this.social_identities)
			ids.add(new SocialInfo(i.getUserID(), i.getUsername(), i.getSocialNetworkID()));
		AppData data = new AppData(AppData.DataType.CROWDAPP, 
				algorithm, nonce, description, specificity, binding_type, 
				sensitivity, policy, created, expires, true,
				ids
				);
		if(!update)
			peerShare.addData(data);
		else {
			try {
				peerShare.updateDataForceDownload(id, data);
			} catch(RemoteException e) {
				//Log.e(TAG,"Remote exception on updating bearer capabilities. " + e.getMessage());
			}
		}
		
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
	    // get the info from the currently running task
	    List< ActivityManager.RunningTaskInfo > taskInfo = am.getRunningTasks(1); 
	    if(taskInfo.get(0).topActivity.getClassName().equals(TetheringActivity.class.getName()))		
	    	Toast.makeText(context, "Token renewed", FoFUnit.tooltipShowDuration).show();
	}
	
	private void updateMyBearerCapabilityData(boolean force, boolean renew) {
		if(!force) {
			peerShare.downloadData();
		} else {
			List<org.sesy.peershare.iface.Long> ids = peerShare.getMyData(AppData.DataType.CROWDAPP);
			boolean update = false, upload=false;
			if(ids.isEmpty()) {
				update = false;
				upload = true;
			} else {
				update = true;
				upload = false;
			}
			
			if(renew || upload) {
				if(update)
					generateBearerCapability(true, ids.get(0).getValue());
				else if(upload)
					generateBearerCapability(false, -1);
			} else
				getBearerCapabilityData();
		}
	}
	
	private boolean isMyBearerCapabilityUploaded() {
		List<org.sesy.peershare.iface.Long> ids = peerShare.getMyData(AppData.DataType.CROWDAPP);
		Log.d(TAG,"Amount of my capabilities: " + ids.size());
		for(org.sesy.peershare.iface.Long id : ids) {
			AppData i = peerShare.getMyDataDetail(id.getValue());
			Log.d(TAG,"Item my data: " + i.getDataDescription() + " " + i.getDataOwnerInfo());
		}
		if(ids.isEmpty())
			return false;
		else
			return true;
	}
	
	private void getBearerCapabilityData() {
		List<AppData> nonces = peerShare.getSharedDataDetails(AppData.DataType.CROWDAPP);
		setMyFriendsData(nonces);
		boolean fresh = true;
		if(!isNetworkAvailable())
			fresh=false;
		token_refresh_callback.FoFTokenRefreshCompleted(fresh);
	}
	
	public class PeerShareServiceListener extends BroadcastReceiver {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(PeerShareService.PEERSHARE_INTENT)) {
				String status = intent.getStringExtra("status");
				//Log.d(TAG,"Received PeerShare intent: " + status);
				if(status.equals("ready")) {
					Logger.i(TAG, "PeerShare service is ready for use",this);
					Log.i(TAG, "PeerShare service is ready for use. Download: " + intent.hasExtra("download"));
					if(intent.hasExtra("download")) {
						getBearerCapabilityData();
						if(!isMyBearerCapabilityUploaded()) {
							Log.d(TAG,"Calling updateMyBearerCapabilityData");
							updateMyBearerCapabilityData(true,false);
						}
					}
				} else if(status.equals("failed")) {
					Logger.e(TAG,"Service unavailable",this);
				} else if(status.equals("offline")) {
					//Log.d(TAG,"We're offline");
					if(my_friends == null || my_friends.isEmpty())
						getBearerCapabilityData();
				}
			}
		}
	}
	
	private void logFoFResult(ProtocolSession session) {
		// Log.i(TAG,"logFoFResult");
		try {
			KeyGenerator keygen = KeyGenerator.getInstance("HmacSHA1");
			Mac mac = Mac.getInstance(keygen.getAlgorithm());
			mac.init(ckey);
			List<ArrayList<SocialInfo> > friends = session.getFinalResults();
			List<String> anonymised_friends = new ArrayList<String>();
			/*for(SocialInfo info : friends) {
				anonymised_friends.add(Base64.encodeToString(mac.doFinal(info.getSocialID().getBytes()), Base64.NO_WRAP));
			}*/
			String f = anonymised_friends.toString();
		
			JSONObject jsonobj = new JSONObject();
			jsonobj.put("local_id", scampiID);
			jsonobj.put("remote_id", session.getRemoteID());
			jsonobj.put("common_friends", f);
			jsonobj.put("direct_friends", session.directFriendship());
			jsonobj.put("start_time",session.getStartTime());
			jsonobj.put("end_time", session.getEndTime());
			jsonobj.put("computation_time", session.getComputationTime());
			jsonobj.put("communication_time", session.getEndTime().getTime() - session.getStartTime().getTime() - session.getCommunicationTime());
			jsonobj.put("input_size", session.getInputSize());
			jsonobj.put("recv_transfer_size", session.getRecvBytes());
			jsonobj.put("send_transfer_size", session.getSentBytes());
			jsonobj.put("role", session.getProtocolRole());
			new FoFLogRequest().execute(this.context.getString(R.string.logDataURL),jsonobj.toString(),"logRequest");
		} catch(JSONException e) {
			Logger.e(TAG,"registerUser exception: " + e.getMessage(),this);
		} catch(InvalidKeyException e) {
			Logger.e(TAG,"Invalid key exception: " + e.getMessage(),this);
		} catch(NoSuchAlgorithmException e) {
			Logger.e(TAG,"No such algorithm exception: " + e.getMessage(),this);
		}
	}
	
	private void fetchFoFLogKey() {
		//Log.i(TAG,"fetchFoFLogKey");
		try {
			JSONObject jsonobj = new JSONObject();
			//jsonobj.put("sn", 1);
			jsonobj.put("ids", this.social_identities);
			//jsonobj.put("token", token);
			new FoFLogRequest().execute(this.context.getString(R.string.keyFetchURL),jsonobj.toString(),"keyRequest");
		} catch(JSONException e) {
			//Log.e(TAG,"registerUser exception: " + e.getMessage());
		}
	}
	
	private void registerUser() {
		//Log.i(TAG,"register user for data logs");
		try {
			JSONObject jsonobj = new JSONObject();
			jsonobj.put("scampi", scampiID);
			jsonobj.put("ids", this.anonymised_ids);
			new FoFLogRequest().execute(context.getString(R.string.logRegisterURL),jsonobj.toString(),"logRegister");
		} catch(JSONException e) {
			// Log.e(TAG,"registerUser exception: " + e.getMessage());
		}
	}
	
	private class FoFLogRequest extends AsyncTask<String,Void,JSONObject> {

		private long begin_ts;
		
		protected JSONObject doInBackground(String... params) {
			JSONObject recvdjson = new JSONObject();
			try {
				begin_ts = System.currentTimeMillis();
				DefaultHttpClient httpClient = new DefaultHttpClient();
				httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
					
					@Override
					public void process(HttpResponse response, HttpContext context)
							throws HttpException, IOException {
				        ManagedClientConnection routedConnection= (ManagedClientConnection)context.getAttribute(ExecutionContext.HTTP_CONNECTION);
				        SSLSession ssl_session = routedConnection.getSSLSession();
				        if(ssl_session != null) {
				            X509Certificate[] certificates= ssl_session.getPeerCertificateChain();
				            if(context.getAttribute(PEER_CERTIFICATES) == null)
				            	context.setAttribute(PEER_CERTIFICATES, certificates);
				        }
					}
				});
				HttpPost httpPostReq = new HttpPost(params[0]);
				HttpContext context = new BasicHttpContext();
				StringEntity se = new StringEntity(params[1]);
				se.setContentType("application/json;charset=UTF-8");
				se.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json; charset=UTF-8"));
				httpPostReq.setEntity(se);
				HttpResponse httpResponse = httpClient.execute(httpPostReq,context);
				InputStream is = FoFUnit.this.context.getAssets().open("se-sy.org.crt");
				try {
					X509Certificate our_server_certificate = X509Certificate.getInstance(is);
					X509Certificate[] peerCertificates = (X509Certificate[])context.getAttribute(PEER_CERTIFICATES);
					if(our_server_certificate.equals(peerCertificates[0])) {
						// Log.d(TAG,"Peer certicate verified successfully.");
					} else {
						// Log.e(TAG, "Wrong certificate received. Terminating connection");
						try {
							recvdjson = new JSONObject();
							recvdjson.put("request-type", params[1]);
							recvdjson.put("error", "Received server certificate not signed by the valid authority");
							return recvdjson;
						} catch(JSONException ex) {
							// Log.e(TAG,"HTTPRequest certificate exception on handling invalid certificate error: " + ex.getMessage());
						}					
					}
				} catch(CertificateException e) {
					// Log.e(TAG, "Certificate exception: " + e.getMessage());
					try {
						recvdjson = new JSONObject();
						recvdjson.put("request-type", params[2]);
						recvdjson.put("error", e.getMessage());
						return recvdjson;
					} catch(JSONException ex) {
						// Log.e(TAG,"HTTPRequest certificate exception on handling certificate error: " + ex.getMessage());
					}
				}
				//Log.d(TAG,"HTTP response status line: " + httpResponse.getStatusLine().toString());
				if(httpResponse.getStatusLine().getStatusCode() == 200) {
					HttpEntity resultEntity = httpResponse.getEntity();
					// Log.d(TAG,"Content length: " + resultEntity.getContentLength());
					InputStream httpstream = resultEntity.getContent();
					String resultstring = convertStreamToString(httpstream);
					httpstream.close();
					Logger.d(TAG, "Received HTTP content: " + resultstring,this);
					recvdjson = new JSONObject(resultstring);
					recvdjson.put("request-type", params[2]);
				} else {
					recvdjson = new JSONObject();
					recvdjson.put("http-error", httpResponse.getStatusLine().getStatusCode());
					recvdjson.put("request-body", params[1]);
					recvdjson.put("request-type", params[2]);
					recvdjson.put("request-url", params[0]);
				}
			} catch(ClientProtocolException e) {
				// Log.e(TAG, "HTTPRequest ClientProtocolException: " + e.getMessage());
				try {
					recvdjson = new JSONObject();
					recvdjson.put("request-type", params[2]);
					recvdjson.put("error", e.getMessage());
				} catch(JSONException ex) {
					// Log.e(TAG,"HTTPRequest JSONException on handling connection error: " + ex.getMessage());
				}
			} catch(IOException e) {
				// Log.e(TAG, "HTTPRequest IOException: " + e.getMessage());
				e.printStackTrace();
				try {
					recvdjson = new JSONObject();
					recvdjson.put("io-error", e.getMessage());
					recvdjson.put("request-type", params[1]);
					recvdjson.put("request-url", params[0]);
				} catch(JSONException ex) {
					// Log.e(TAG,"HTTPRequest JSON exception on handling IO exception: " + ex.getMessage());
				}
			} catch (JSONException e) {
				// Log.e(TAG,"HTTPRequest JSONException: " + e.getMessage());
			}

			return recvdjson;
		}

		protected void onPostExecute(JSONObject json) {
			//Log.d(TAG,"onPostExecute; JSON=" + json.toString());
			//Log.i(TAG,"Transaction time: " + ((System.currentTimeMillis() - begin_ts)/1000.0));
			String request_type = new String();
			try {
				if(json.has("http-error")) {
					handleHttpError(json);
				} else if(json.has("io-error")) {
					handleNetworkError(json);
				} else {
					request_type = json.getString("request-type");
					if(request_type.equals("keyRequest"))
						handleKeyResponse(json);
					else if(request_type.equals("registerRequest"))
						handleRegisterResponse(json);
					else if(request_type.equals("logRequest"))
						handleLogRequest(json);
				}
			} catch(JSONException e) {
				// Log.e(TAG,"HTTPRequest onPostExecute JSONException: " + e.getMessage());
			}

		}
		
		private void handleKeyResponse(JSONObject json) {
			try {
				if(json.has("error")) {
					Logger.e(TAG, "Unable to fetch FoF log key. Logging is disabled",this);
				} else {
					String status = json.getString("status");
					// Log.d(TAG,"Handle key response status: " + status);
					byte[] ckey_raw = Base64.decode(json.getString("key"),Base64.NO_WRAP);
					ckey = new SecretKeySpec(ckey_raw, 0, ckey_raw.length, "HmacSHA1");
					// Log.d(TAG,"Log key created: " + ckey + " scampi ID: " + scampiID);
					
					KeyGenerator keygen = KeyGenerator.getInstance("HmacSHA1");
					Mac mac = Mac.getInstance(keygen.getAlgorithm());
					mac.init(ckey);
					
					for(SocialNetworkAccount a : FoFUnit.this.social_identities) {
						//byte[] myID = Long.toString(myFacebookID).getBytes();
						byte[] myID = a.getUserID().getBytes();
						byte[] digest = mac.doFinal(myID);
						String anonymisedID = Base64.encodeToString(digest,Base64.NO_WRAP);
						FoFUnit.this.anonymised_ids.put(a.getSocialNetworkID(), anonymisedID);
						//anonymisedFacebookID = Base64.encodeToString(digest,Base64.NO_WRAP);
						/*if(scampiID!=null) {
							registerUser(anonymisedFacebookID);
						} else {
							Logger.e(TAG,"Scampi ID unassigned. Data collection disabled",this);
						}*/
					}
					if(scampiID!=null) {
						registerUser();
					} else {
						Logger.e(TAG,"Scampi ID unassigned. Data collection disabled",this);
						//Log.e(TAG,"Scampi ID unassigned. Data collection disabled");
					}
				}
			} catch(JSONException e) {
				Logger.e(TAG,"HTTPRequest handleRegisterResponse JSONException: " + e.getMessage(),this);
			} catch(InvalidKeyException e) {
				Logger.e(TAG,"Invalid key excpetion on sending anonymised ID: " + e.getMessage(),this);
			} catch(NoSuchAlgorithmException e) {
				Logger.e(TAG,"No such algorithm exception: " + e.getMessage(),this);
			}
		}
		
		private void handleRegisterResponse(JSONObject json) {
			// Log.i(TAG,"HTTPRequest handleRegisterResponse input: " + json.toString());
			try {
				if(json.has("error")) {
					Logger.e(TAG,"Handle key register response error: " + json.getString("error"),this);
					Logger.e(TAG, "FoF results logging disabled",this);
				} else {
					String status = json.getString("status");
					Logger.d(TAG,"Handle key register response status: " + status,this);
				}
			} catch(JSONException e) {
				// Log.e(TAG,"HTTPRequest handleRegisterResponse JSONException: " + e.getMessage());
			}
		}
		
		private void handleLogRequest(JSONObject json) {
			// Log.i(TAG,"HTTPRequest handleLogRequest input: " + json.toString());
			try {
				if(json.has("error")) {
					Logger.e(TAG,"Handle FoF results log response error: " + json.getString("error"),this);
					Logger.e(TAG, "FoF results logging disabled",this);					
				} else {
					String status = json.getString("status");
					// Log.d(TAG,"Handle register response status: " + status);
				}
			} catch(JSONException e) {
				// Log.e(TAG,"HTTPRequest handleRegisterResponse JSONException: " + e.getMessage());
			}
		}
		
		private void handleHttpError(JSONObject json) {
			Intent intent = new Intent(context.getString(R.string.httpclientintent));
			int code = -1;
			try {
				code = json.getInt("http-error");
			} catch(JSONException e) {
				// Log.e(TAG,"handleHttpError JSON exception: " + e.getMessage());
			}
			if(code == 500)
				intent.putExtra("http-error", "Internal PeerSense server error");
			else
				intent.putExtra("http-error", code);
			try {
				intent.putExtra("request-type", json.getString("request-type"));
				intent.putExtra("request-body", json.getString("request-body"));
				intent.putExtra("request-url", json.getString("request-url"));
			} catch(JSONException e) {
				// Log.e(TAG,"handleHttpError JSON exception: " + e.getMessage());
			}
			//LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
		
		private void handleNetworkError(JSONObject json) {
			Intent intent = new Intent(context.getString(R.string.httpclientintent));
			intent.putExtra("io-error", "Network unavailable");
			try {
				intent.putExtra("request-type", json.getString("request-type"));
				intent.putExtra("request-body", json.getString("request-body"));
				intent.putExtra("request-url", json.getString("request-url"));
			} catch(JSONException e) {
				// Log.e(TAG,"handleHttpError JSON exception: " + e.getMessage());
			}
			//LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
		
		private String convertStreamToString(InputStream is) {
			String line = "";
			StringBuilder total = new StringBuilder();
			BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			try {
				while ((line = rd.readLine()) != null) {
					total.append(line);
				}
			} catch (Exception e) {
				//Toast.makeText(this, "Stream Exception", Toast.LENGTH_SHORT).show();
			}
			return total.toString();
		}
	}
	
	public class TimeoutTask extends TimerTask {
		
		private String sessionID;
		private String hostID;
		
		public TimeoutTask(String session_id, String host_id) {
			this.sessionID = session_id;
			this.hostID = host_id;
		}
		
		@Override
		public void run() {
			Logger.w(TAG, "No response for FoF request for host: " + sessionID + ". Aborting protocol",this);
			if(FoFUnit.this.sessions.containsKey(sessionID)) {
				FoFUnit.this.sessions.remove(sessionID);
				timeout_callback.FoFProtocolTimeout(hostID);
				//sessions.remove(sessionID);
			}
			this.cancel();
		}
	}
}


