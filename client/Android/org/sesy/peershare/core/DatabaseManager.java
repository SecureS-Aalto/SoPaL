package org.sesy.peershare.core;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sesy.peershare.iface.AppData;
import org.sesy.peershare.iface.SocialInfo;
import org.sesy.socialauth.SocialNetworkAccount;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseManager extends SQLiteOpenHelper {

	/**
	 * The database that the provider uses as its underlying data store
	 */
	private static final String DATABASE_NAME = "peersense_bindings.db";

	/**
	 * The database version
	 */
	private static final int DATABASE_VERSION = 2;

	private static final String SOCIAL_TABLE = "socialName";

	private static final String SECRET_INFO = "SecretInfo";

	private static final String BINDINGS = "Bindings";
	
	private static final String USER_INFO = "userInfo";
	
	private static final String MY_SECRETS = "mySecrets";
	
	private static final String PENDING_ACTIONS = "pendingActions";
	
	private static final String TAG = "DatabaseManager";
	
	private Context context;
	
	DatabaseManager(Context context) {

		// calls the super constructor, requesting the default cursor factory.
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		this.context = context;
	}

	/**
	 *
	 * Creates the underlying database with table name and column names taken from the
	 * NotePad class.
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		try {
			db.execSQL("CREATE TABLE IF NOT EXISTS " + SOCIAL_TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT,social_type TEXT not null , social_name TEXT not null,social_id TEXT NOT NULL,UNIQUE (social_type, social_id,id))");
			db.execSQL("CREATE TABLE IF NOT EXISTS " + SECRET_INFO + " (type INTEGER NOT NULL, algorithm TEXT NOT NULL, value TEXT NOT NULL, description TEXT NOT NULL, created TIMESTAMP NOT NULL, expires TIMESTAMP, sensitivity INTEGER, platform_id INTEGER NOT NULL, platform_app_id TEXT NOT NULL, pseudo TEXT, id INTEGER PRIMARY KEY AUTOINCREMENT)");
			db.execSQL("CREATE TABLE IF NOT EXISTS " + BINDINGS + " (secret_id INTEGER REFERENCES " + SECRET_INFO + "(id), social_id INTEGER REFERENCES " + SOCIAL_TABLE + "(id), binding_type INTEGER NOT NULL, obsolete BOOLEAN NOT NULL, PRIMARY KEY(secret_id,social_id,binding_type))");
			db.execSQL("CREATE TABLE IF NOT EXISTS " + USER_INFO + " (key TEXT PRIMARY KEY, value TEXT)");
			db.execSQL("CREATE TABLE IF NOT EXISTS " + MY_SECRETS + " (type INTEGER NOT NULL, algorithm TEXT NOT NULL, value TEXT NOT NULL, policy TEXT NOT NULL, description TEXT NOT NULL, SNtype INTEGER NOT NULL, created TIMESTAMP NOT NULL, expires TIMESTAMP, share INTEGER, specificity INTEGER, sensitivity INTEGER, binding_type INTEGER, platform_id INTEGER NOT NULL, platform_app_id TEXT NOT NULL, object_id INTEGER NOT NULL, local_id INTEGER PRIMARY KEY AUTOINCREMENT)");
			db.execSQL("CREATE TABLE IF NOT EXISTS " + PENDING_ACTIONS + " (id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL, body TEXT NOT NULL, url TEXT NOT NULL)");
		} catch(SQLException e) {
			// Log.e("DatabaseHelper - onCreate()", e.getMessage());
		}
	}

	/**
	 *
	 * Demonstrates that the provider must consider what happens when the
	 * underlying datastore is changed. In this sample, the database is upgraded the database
	 * by destroying the existing data.
	 * A real application should upgrade the database in place.
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		// Logs that the database is being upgraded
		/*Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
				+ newVersion + ", which will destroy all old data"); */

		try {
			// Kills the table and existing data
			db.execSQL("DROP TABLE IF EXISTS " + SOCIAL_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + SECRET_INFO);
			db.execSQL("DROP TABLE IF EXISTS " + BINDINGS);
			db.execSQL("DROP TABLE IF EXISTS " + USER_INFO);
			db.execSQL("DROP TABLE IF EXISTS " + MY_SECRETS);
			db.execSQL("DROP TABLE IF EXISTS " + PENDING_ACTIONS);
		} catch(SQLException e) {
			// Log.e("DatabaseHelper - onUpgrade()", e.getMessage());
		}
		// Recreates the database with a new version
		onCreate(db);
	}


	public void dropSocialTables() {
		SQLiteDatabase db = this.getWritableDatabase();
		try {
			db.execSQL("DROP TABLE IF EXISTS " + SOCIAL_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + BINDINGS);
			db.execSQL("DROP TABLE IF EXISTS " + MY_SECRETS);
			db.execSQL("DROP TABLE IF EXISTS " + SECRET_INFO);
		} catch(SQLException e) {
			// Log.e(TAG,"dropSocailTables " + e.getMessage());
		}
		db.close();
	}

	/* old deleteDBInfoFromFbApp() */
	/*public void removeExistingBTBinding(String deviceID, String socialNet, String snID) {
		SQLiteDatabase db = this.getWritableDatabase();
		Cursor dev_cursor = db.query(BT_DEVICE_INFO,new String[] {"id"},"deviceID='"+deviceID+"'",null,null,null,null,null);
		if(dev_cursor != null && dev_cursor.moveToFirst()) {
			int dev_id = dev_cursor.getInt(0);
			Cursor person_cursor = db.query(SOCIAL_TABLE,new String[] {"id"},"social_id='?' AND social_type='?'",new String[] {String.valueOf(snID),String.valueOf(socialNet)},null,null,null,null);
			if(person_cursor != null && person_cursor.moveToFirst()) {
				int social_id = person_cursor.getInt(0);
				db.delete(BT_BINDINGS,"device= ? AND social= ? AND binding_type='u'", new String[] {String.valueOf(dev_id),String.valueOf(social_id)});
			} else {
				Log.w(TAG,"removeExistingBinding: Unable to remove the binding. Social info not found");
			}
		} else {
			Log.w(TAG,"removeExisitingBinding: Unable to remove the binding. Device not found");
		}
	}*/
	
	public void initDb() {
		onCreate(getWritableDatabase());
	}
	
	public void updateBindings() {
		/* implementation needed */
	}
	
	public void printMySecretsDebug() {
		SQLiteDatabase db = this.getReadableDatabase();
		// Log.d(TAG,"printMySecretsDebug");
		Cursor cursor = db.query(MY_SECRETS, new String[] {"local_id", "object_id", "description"}, null, null, null, null, null);
		if(cursor!=null && cursor.moveToFirst()) {
			do {
				// Log.d(TAG,"Object ID: " + cursor.getString(1) + " local ID: " + cursor.getString(0) + " description: " + cursor.getString(2));
			} while(cursor.moveToNext());
			cursor.close();
		}
	}
	
	public void printSecretInfoDebug() {
		SQLiteDatabase db = this.getReadableDatabase();
		// Log.d(TAG,"printSecretInfoDebug");
		Cursor cursor = db.query(SECRET_INFO, new String[] {"id", "description"}, null, null, null, null, null);
		if(cursor!=null && cursor.moveToFirst()) {
			do {
				// Log.d(TAG,"Id: " + cursor.getString(0) + " description: " + cursor.getString(1));
			} while(cursor.moveToNext());
			cursor.close();
		}
	}
	
	public void printSocialInfoDebug() {
		SQLiteDatabase db = this.getReadableDatabase();
		// Log.d(TAG,"printSocialInfoDebug");
		Cursor cursor = db.query(SOCIAL_TABLE, new String[] {"social_type", "social_name", "social_id"}, null, null, null, null, null);
		if(cursor!=null && cursor.moveToFirst()) {
			do {
				// Log.d(TAG,"Name: " + cursor.getString(1) + " id: " + cursor.getString(2) + " type: " + cursor.getString(0));
			} while(cursor.moveToNext());
			cursor.close();
		}
	}
	
	public boolean updateSecretsFromServer(HashMap<Long, SecretDetails> secrets) {
		boolean ok=true;
		PackageManager mgr = context.getPackageManager();
		SQLiteDatabase db = this.getWritableDatabase();
		
		db.beginTransaction();
		db.delete(SOCIAL_TABLE, null, null);
		db.delete(BINDINGS, null, null);
		db.delete(SECRET_INFO,null,null);
		
		Log.d(TAG,"updateSecretsFromServer. Number of data items: " + secrets.size());
		
		Collection<SecretDetails> data = secrets.values();
		Iterator<SecretDetails> it = data.iterator();
		while(it.hasNext()) {
			SecretDetails secret = it.next();
			
			//Log.d(TAG,"Storing item: " + secret.getSecretText() + " object ID: " + secret.getObjectID());
			
			/*long social_id = secret.getSocialID();
			int sn_type = secret.getSecretSocialNetworkType();
			String username = secret.getSocialUsername();*/
			List<SocialInfo> owners = secret.getSecretDataOwners();
			
			Map<SocialInfo,Long> social_record_ids = new HashMap<SocialInfo, Long>();
			
			for(SocialInfo i : owners) {
				//Log.d(TAG,"Checking if user : " + i.getSocialID() + " " + i.getNetworkID() + " is stored");
				Cursor social_exists = db.query(SOCIAL_TABLE,new String[] {"id","social_id","social_type","social_name"},
						"social_id=? AND social_type=?",new String[] {i.getSocialID(), String.valueOf(i.getNetworkID())},null,null,null);
				if(social_exists!=null && social_exists.moveToFirst()) {
					social_record_ids.put(i,social_exists.getLong(0));
					//Log.d(TAG,"User : " + i.getSocialID() + " " + i.getNetworkID() + " is already stored");
				} else {
					//Log.d(TAG,"Adding user : " + i.getSocialID() + " " + i.getNetworkID());
					ContentValues social_data = new ContentValues();
					social_data.put("social_type",i.getNetworkID());
					social_data.put("social_name",i.getSocialName());
					social_data.put("social_id",i.getSocialID());
					if(db.insert(SOCIAL_TABLE, null, social_data) == -1) {
						// Log.e(TAG,"Error on updating social table in updateSecretsFromServer");
						ok=false;
						break;
					}
					Cursor secret_cursor = db.rawQuery("SELECT last_insert_rowid()",null);
					if(secret_cursor!=null && secret_cursor.moveToFirst()) {
						social_record_ids.put(i,secret_cursor.getLong(0));
						secret_cursor.close();
					}
				}
				if(social_exists!=null)
					social_exists.close();
			}
			
			ContentValues secret_data = new ContentValues();
			secret_data.put("type", secret.getSecretType());
			secret_data.put("algorithm", secret.getSecretAlgorithm());
			secret_data.put("value", secret.getSecretValue());
			secret_data.put("description", secret.getSecretText());
			secret_data.put("created", secret.getSecretCreationTS().toString());
			secret_data.put("expires", secret.getSecretExpiryTS().toString());
			secret_data.put("platform_id", secret.getPlatformID());
			secret_data.put("platform_app_id", secret.getPlatformAppID());
			//secret_data.put("pseudo", generatePseudonym(secret.getSecretValue(), secret.getSocialUsername()));
			if(db.insert(SECRET_INFO, null, secret_data) == -1) {
				// Log.e(TAG,"Error on updating secret info table in updateSecretsFromServer");
				ok=false;
				break;
			} else {
				//Log.d(TAG,"Inserted data into SECRET_INFO");
			}
			
			long secret_record_id = -1;
			Cursor secret_cursor = db.rawQuery("SELECT last_insert_rowid()",null);
			if(secret_cursor!=null && secret_cursor.moveToFirst()) {
				secret_record_id = secret_cursor.getLong(0);
			} else {
				// Log.e(TAG,"Error on obtaining record ID in secret info table in updateSecretsFromServer");
				ok=false;
				break;				
			}
			if(secret_cursor!=null)
				secret_cursor.close();
			
			for(SocialInfo i : owners) {
				Long social_id = social_record_ids.get(i);
				ContentValues binding_data = new ContentValues();
				binding_data.put("secret_id",secret_record_id);
				binding_data.put("social_id", social_id);
				binding_data.put("binding_type",AppData.BindingType.OWNER_ASSERTED);
				binding_data.put("obsolete",0);
				if(db.insert(BINDINGS, null, binding_data) == -1) {
					//Log.e(TAG,"Error on updating binding table in updateSecretsFromServer");
					ok=false;
					break;
				}
			}
			
			// Less than 0 are forbidden values for objectID look at SecretDetails definitions
			if(secret.getObjectID() > 0) {
				Log.d(TAG,"It's my secret");
				PackageInfo pkg_info = null;
				try {
					String[] items = secret.getPlatformAppID().split(";");
					// Log.d(TAG,"Checking package: " + items[0]);
					if(items.length == 2) {
						pkg_info = mgr.getPackageInfo(items[0], 0);
						Log.d(TAG,"Adding my data: " + secret.getSecretText() + " user: " + owners.get(0).getSocialName() + " sntype: " + owners.get(0).getNetworkID());
						addMySecret(secret.getSecretType(), secret.getSecretAlgorithm(), secret.getSecretValue(), secret.getSecretText(), 
								secret.getSecretPolicy(), secret.getSecretSpecificity(), secret.getSecretSensitivity(), secret.getBindingType(),
								-1, true, secret.getObjectID(),secret.getSecretExpiryTS(),secret.getSecretCreationTS(),
								pkg_info.applicationInfo.uid);
					} else {
						// Log.e(TAG,"Wrong platform app id. Secret not added to my secrets");
					}
				} catch(NameNotFoundException e) {
					// Log.e(TAG,"Package name not found. Secret not added to my secrets");
				}
			}
			
		}
		
		ContentValues last_contact_ts = new ContentValues();
		last_contact_ts.put("key", "last_server_download");
		last_contact_ts.put("value", System.currentTimeMillis());
		if(db.replace(USER_INFO, null, last_contact_ts) == -1) {
			ok = false;
		}
		
		if(ok)
			db.setTransactionSuccessful();
		db.endTransaction();
		
		//printSecretInfoDebug();
		//printMySecretsDebug();
		//printSocialInfoDebug();
		// Log.d(TAG,"End of download operation");
		
		return ok;
	}
	
	public void updateDBInfoFromBt() {
		/* implementation needed */
	}
	
	private boolean checkIfUserRegistered() {
		boolean cont = false;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(USER_INFO, new String[] {"value"},"key='psID'",null,null,null,null,null);
		if(cursor != null && cursor.moveToFirst() && !cursor.getString(0).equals("")) {
			cont = true;
		}
		if(cursor!=null)
			cursor.close();
		return cont;
	}
	
	public Map<Integer,SocialNetworkAccount> getRegisteredSocialNetworkAccounts() {
		SQLiteDatabase db = this.getWritableDatabase();
		Map<Integer,SocialNetworkAccount> accounts = new HashMap<Integer,SocialNetworkAccount>();
		
		db.beginTransaction();
			
		Cursor id = db.query(USER_INFO, new String[] {"key","value"}, "key LIKE ?",new String[] {"%-snID"},null,null,null);
		Cursor type = db.query(USER_INFO, new String[] {"key","value"}, "key LIKE ?",new String[] {"%-snType"},null,null,null);
		if(type!=null && type.moveToFirst()) {
			do {
				String key = type.getString(0).split("-")[0];
				SocialNetworkAccount account = new SocialNetworkAccount();
				account.setSocialNetworkID(Integer.valueOf(key));
				accounts.put(Integer.valueOf(key), account);
			} while(type.moveToNext());
		}
		if(id!=null && id.moveToFirst()) {
			do {
				int sn_type = Integer.valueOf(id.getString(0).split("-")[0]);
				String user_id = id.getString(1);
				SocialNetworkAccount account = accounts.get(Integer.valueOf(sn_type));
				account.setUserID(user_id);
			} while(id.moveToNext());
		}
		if(type!=null)
			type.close();
		if(id!=null)
			id.close();
		db.endTransaction();
				
		return accounts;
	}
	
	public long getUserID() {
		long ret = -1;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(USER_INFO, new String[] {"value"},"key='psID'",null,null,null,null,null);
		if(cursor != null && cursor.moveToFirst())
			ret = cursor.getLong(0);
		if(cursor!=null)
			cursor.close();
		return ret;		
	}
	
	public boolean registerUser(long PSid, String SNid, int snType, String snName) {
		boolean ok = false;
		SQLiteDatabase db = this.getWritableDatabase();
				
		//Log.d(TAG,"registerUser: PSid=" + PSid + " SNid=" + SNid + " snType: " + snType);
		db.beginTransaction();
		int number = 0;
		Cursor cursor = db.query(USER_INFO,new String[] {"value"},"key='snNo'",null,null,null,null,null);
		if(cursor!=null && cursor.moveToFirst())
			number = cursor.getInt(0);
		if(cursor!=null)
			cursor.close();
		//Log.d(TAG,"Number of active accounts: " + number);
		number++;
		ContentValues psid_values = new ContentValues(), snid_values = new ContentValues(), sntype_values = new ContentValues(), 
			snname_values = new ContentValues(), number_values = new ContentValues();
		psid_values.put("key", "psID");
		psid_values.put("value", PSid);
		number_values.put("key", "snNo");
		number_values.put("value", number);
		snid_values.put("key", snType + "-snID");
		snid_values.put("value", SNid);
		sntype_values.put("key", snType + "-snType");
		sntype_values.put("value", snType);
		snname_values.put("key", snType + "-snName");
		snname_values.put("value", snName);
		//Log.d(TAG,"Adding keys to user info table: " + snType + "-snID " + snType + "-snType " + snType + "-snName");
		if((db.replace(USER_INFO, null, psid_values) != -1) && (db.replace(USER_INFO, null,snid_values) != -1) 
				&& (db.replace(USER_INFO, null,sntype_values) != -1) && (db.replace(USER_INFO, null, snname_values) != -1)
				&& (db.replace(USER_INFO,null,number_values) != -1)) {
			db.setTransactionSuccessful();
			ok = true;
		}
		db.endTransaction();
		//Log.d(TAG,"registerUser returns: " + ok);
		return ok;		
	}
	
	public boolean unregisterUserAccount(int snID) {
		boolean ok = false;
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		int number = 0;
		Cursor cursor = db.query(USER_INFO,new String[] {"value"},"key='snNo'",null,null,null,null,null);
		if(cursor!=null && cursor.moveToFirst())
			number = cursor.getInt(0);
		if(cursor!=null)
			cursor.close();
		//Log.d(TAG,"Number of active accounts: " + number);
		if(number == 0)
			return false;
		
		number--;
		ContentValues snNo = new ContentValues();
		snNo.put("key", "snNo");
		snNo.put("value", number);
		if(db.replace(USER_INFO, null, snNo) != -1) {
			String snType = String.valueOf(snID) + "-snType";
			String sn_ID = String.valueOf(snID) + "-snID";
			String snName = String.valueOf(snID) + "-snName";
			//Log.d(TAG,"Created keys for delete: " + snType + " " + sn_ID + " " + snName);
			if((db.delete(USER_INFO, "key=?", new String[] {snType}) > 0) && (db.delete(USER_INFO, "key=?", new String[] {sn_ID}) > 0)
					&& (db.delete(USER_INFO,"key=?",new String[] {snName}) > 0)) {
				db.setTransactionSuccessful();
				ok=true;
			}
		}
		db.endTransaction();
		//Log.d(TAG,"unregisterUserAccount returns: " + ok);
		return ok;	
	}
	
	public boolean unregisterUser() {
		boolean ok = false;
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		int number = 0;
		Cursor cursor = db.query(USER_INFO,new String[] {"value"},"key='snNo'",null,null,null,null,null);
		if(cursor!=null && cursor.moveToFirst())
			number = cursor.getInt(0);
		if(cursor!=null)
			cursor.close();
		//Log.d(TAG,"Number of active accounts: " + number);
		if((db.delete(USER_INFO, "key='snNo'", null) > 0) && (db.delete(USER_INFO,"key=psID",null) > 0)) {
			for(int i=0; i<number; i++) {
				String snIDKey = String.valueOf(i) + "-snID";
				String snNameKey = String.valueOf(i) + "-snName";
				String snTypeKey = String.valueOf(i) + "-snType";
				if((db.delete(USER_INFO, snIDKey, null) <= 0) || (db.delete(USER_INFO, snNameKey, null) <= 0) 
						|| (db.delete(USER_INFO, snTypeKey, null) <= 0)) {
					db.endTransaction();
					return ok;
				}
			}
			db.setTransactionSuccessful();
			ok=true;
		}
		db.endTransaction();
		return ok;		
	}
	
	/*public boolean updateAvailableFacebookUserLists(ArrayList<SocialNetworkUserListItem> list) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		ContentValues count = new ContentValues();
		count.put("key", "FacebookList.count");
		count.put("value", list.size());
		if(db.replace(USER_INFO, null, count) == -1) {
			// Log.e(TAG,"Error while updating number of available Facebook lists");
			db.endTransaction();
			return false;
		}
		for(int i=0; i<list.size(); i++) {
			SocialNetworkUserListItem item = list.get(i);
			ContentValues name = new ContentValues(), id = new ContentValues();
			String name_key = new String("FacebookList." + i + ".name");
			name.put("key",name_key);
			name.put("value",item.getListName());
			String id_key = new String("FacebookList." + i + ".id");
			id.put("key", id_key);
			id.put("value",item.getListID());
			if((db.replace(USER_INFO, null, name) == -1) || (db.replace(USER_INFO, null, id) == -1)) {
				db.endTransaction();
				return false;
			}
		}
		db.setTransactionSuccessful();
		db.endTransaction();
		
		Cursor counter = db.query(USER_INFO,new String[] {"value"},"key='FacebookList.count'",null,null,null,null,null);
		if(counter != null && counter.moveToFirst())
			// Log.i(TAG,"FacebookList.count retrieved value: " + counter.getInt(0));
		else
			// Log.d(TAG,"Unable to get FacebookList.count value");
		
		return true;
	}*/
	
	/*public ArrayList<SocialNetworkUserListItem> getAvailableFacebookUserLists() {
		boolean ok=true;
		ArrayList<SocialNetworkUserListItem> tmp = new ArrayList<SocialNetworkUserListItem>();
		SQLiteDatabase db = this.getReadableDatabase();
		db.beginTransaction();
		int ret = -1;
		Cursor counter = db.query(USER_INFO,new String[] {"value"},"key='FacebookList.count'",null,null,null,null,null);
		if(counter != null && counter.moveToFirst())
			ret = counter.getInt(0);
		if(ret > 0) {
			for(int i=0; i<ret;i++) {
				String key_name = new String("FacebookList." + i + ".name");
				String key_id = new String("FacebookList." + i + ".id");
				Cursor name = db.query(USER_INFO,new String[] {"value"},"key=?",new String[] {key_name},null,null,null,null);
				Cursor id = db.query(USER_INFO,new String[] {"value"},"key=?",new String[] {key_id},null,null,null,null);
				if(name != null && id != null) {
					name.moveToFirst();
					id.moveToFirst();
					SocialNetworkUserListItem item = new SocialNetworkUserListItem(id.getLong(0), name.getString(0), new String("Facebook"));
					tmp.add(item);
				} else {
					// Log.e(TAG,"Error while obtaining Facebook list from the database");
					ok=false;
					break;
				}
			}
		}
		if(ok == true)
			db.setTransactionSuccessful();
		db.endTransaction();
		return tmp;
	}*/
	
	public long addMySecret(int type, String algorithm, String value, String description, String policy, int specificity, int sensitivity, int binding_type, 
			long SNtype, boolean share, int uid) {
		Timestamp expiry = new Timestamp((Long.MAX_VALUE/100000));
		Timestamp created_ts = new Timestamp(System.currentTimeMillis());
		return addMySecret(type, algorithm, value, description, policy, specificity, sensitivity, binding_type, SNtype, share, -1,expiry,created_ts,uid); 
	}
	
	public long addMySecret(int type, String algorithm, String value, String description, String policy, int specificity, int sensitivity, int binding_type, 
			long SNtype, boolean share, Timestamp expiry, Timestamp created, int uid) {
		return addMySecret(type, algorithm, value, description, policy, specificity, sensitivity, binding_type, SNtype, share, -1,expiry,created,uid); 
	}
	
	public long addMySecret(int type, String algorithm, String value, String description, String policy, int specificity, int sensitivity, int binding_type,
			long SNtype, boolean share, long object_id, int uid) {
		Timestamp expiry = new Timestamp((Long.MAX_VALUE/100000));
		Timestamp created_ts = new Timestamp(System.currentTimeMillis());
		return addMySecret(type, algorithm, value, description, policy, specificity, sensitivity, binding_type, SNtype, share, object_id,expiry,created_ts,uid); 
	}
	
	public long addMySecret(int type, String algorithm, String value, String description, String policy, int specificity, int sensitivity, int binding_type,
			long SNtype, boolean share, long object_id, Timestamp expiry, Timestamp created, int uid) {
		boolean ok = false;
		
		PackageManager mgr = context.getPackageManager();
		String package_name = mgr.getNameForUid(uid);
		PackageInfo info = null;
		try {
			info = mgr.getPackageInfo(mgr.getNameForUid(uid), PackageManager.GET_SIGNATURES);
		} catch(NameNotFoundException e) {
			// Log.e(TAG,"Package info not found for the given name. Signature verification failed");
			return PeerShareService.APP_AUTH_ERROR;
		}
		Signature[] signs = info.signatures;
		
		SQLiteDatabase db = this.getWritableDatabase();		
		boolean add_new = false;
		
		if(object_id == SecretDetails.UNASSIGNED)
			add_new=true;
		else if(object_id == SecretDetails.UNKNOWN)
			add_new = false;
		else {
			Cursor check = db.query(MY_SECRETS,new String[] {"object_id","platform_id","platform_app_id"},"object_id=?",new String[] {String.valueOf(object_id)},null,null,null,null);		
			
			try {		
				if(check == null)
					add_new = true;
				else if(check.moveToFirst() == false)
					add_new = true;
				else {
					if((check.getLong(0) != object_id) && object_id!=SecretDetails.UNKNOWN)// || (object_id==-1 && check.getLong(0)==-1))
						add_new = true;
				}
				
				if(!add_new) { // check if signatures match
					String[] parts = check.getString(2).split(";");
					if(parts.length != 2)
						return PeerShareService.APP_AUTH_ERROR;
					if((check.getInt(1)!=PeerShareService.MobilePlatform.ANDROID) || (!signs[0].toCharsString().equals(parts[1]))) {
						return PeerShareService.APP_AUTH_ERROR; // signature mismatch
					}
				}
			} finally {
				if(check!=null)
					check.close();
			}
		}
				
		if(add_new) {		
			// Log.d(TAG,"Inserting new secret to my secrets table: description=" + description);
			//Log.d(TAG,"Inserting new data to MY_SECRETS table snType: " + SNtype);
			ContentValues secret_data = new ContentValues();
			secret_data.put("type", type);
			secret_data.put("algorithm", algorithm);
			secret_data.put("value", value);
			secret_data.put("description", description);
			secret_data.put("specificity", specificity);
			secret_data.put("sensitivity", sensitivity);
			secret_data.put("binding_type", binding_type);
			secret_data.put("policy", "Friends");
			secret_data.put("SNtype",SNtype);
			secret_data.put("created", created.toString());
			secret_data.put("expires", expiry.toString());
			secret_data.put("object_id", object_id);
			secret_data.put("platform_id", PeerShareService.MobilePlatform.ANDROID);
			secret_data.put("platform_app_id", package_name + ";" + signs[0].toCharsString());
			if(share)
				secret_data.put("share", 1);
			else
				secret_data.put("share", 0);
			if(db.replace(MY_SECRETS, null, secret_data) != -1)
				ok = true;
		} else {
			
			ContentValues secret_update = new ContentValues();
			secret_update.put("created", created.toString());
			if(share)
				secret_update.put("share", 1);
			else
				secret_update.put("share", 0);		
			if(db.update(MY_SECRETS, secret_update,"object_id=?", new String[] {String.valueOf(object_id)}) == 1)
				ok=true;
		}
		long local_id = -1;
		Cursor getID = db.query(MY_SECRETS,new String[] {"local_id"},"type=? AND algorithm=? AND value=? AND description=? AND SNtype=?",new String[] {String.valueOf(type),algorithm,value,description,String.valueOf(SNtype)},null,null,null,null);
		if(getID != null && getID.moveToFirst()) {
			local_id = getID.getInt(0);
		}
		if(getID!=null)
			getID.close();
		return local_id;
	}
	
	// Updates my secret locally. As secret key, local object ID is used.
	public int updateMySecret(long local_objectID, AppData data, int uid) {
		int ok = -1;
		if(verifyAppSignature(local_objectID, uid, true) == false) {
			ok = PeerShareService.APP_AUTH_ERROR;
		} else {
			SQLiteDatabase db = this.getWritableDatabase();
			//Log.d(TAG,"App data to update: " + data);
			ContentValues updated = new ContentValues();
			updated.put("type", data.getDataType());
			updated.put("algorithm", data.getDataAlgorithm());
			updated.put("value", data.getDataValue());
			updated.put("sensitivity", data.getDataSensitivity());
			updated.put("specificity", data.getDataSpecificity());
			updated.put("binding_type", data.getDataBindingType());
			updated.put("description", data.getDataDescription());
			updated.put("policy", data.getSharingPolicy());
			//updated.put("SNtype",data.getDataOwnerNetworkID());
			updated.put("SNtype", -1);
			updated.put("created", data.getCreationTimestamp().toString());
			updated.put("expires", data.getExpiryTimestamp().toString());
			//updated.put("object_id", objectID);
			if(data.isSharable())
				updated.put("share", 1);
			else
				updated.put("share", 0);
			if(db.update(MY_SECRETS, updated, "local_id=?", new String[] { String.valueOf(local_objectID) }) == 1)
				ok = 0;
		}
		
		//printMySecretsDebug();
		
		return ok;
	}
	
	// This method returns object details for given local object ID
	public AppData getMySecretDataForApp(long local_id, int uid) {
		SQLiteDatabase db = this.getReadableDatabase();
		AppData data = null;
		
		if(getRegisteredSocialNetworkAccounts().isEmpty()) {
			//Log.e(TAG,"No social data available returning null");
			return data;
		}
						
		Cursor cursor = db.query(MY_SECRETS,new String[] {"type","algorithm","value","policy","specificity","sensitivity", "binding_type", "description","created","expires","share","platform_id","platform_app_id","SNtype"},"local_id=?",new String[] {String.valueOf(local_id)},null,null,null);
		if(cursor!=null && cursor.moveToFirst()) {
			String[] items = cursor.getString(12).split(";");
			if(items.length==2 && verifyAppSignature(local_id, uid, true)) {
					boolean share = true;
					if(cursor.getInt(7) == 0)
						share=false;
					//Log.d(TAG,"App signature verified successfully");
					Map<Integer,SocialNetworkAccount> accounts = getRegisteredSocialNetworkAccounts();
					List<SocialInfo> owner = new ArrayList<SocialInfo>();
					for(Integer i : accounts.keySet()) {
						SocialNetworkAccount account = accounts.get(i);
						//Log.d(TAG,"Registered account: " + account.getUserID() + " " + account.getUsername() + " " + account.getSocialNetworkID());
						owner.add(new SocialInfo(account.getUserID(),account.getUsername(),account.getSocialNetworkID()));
					}
					//SocialNetworkAccount account = accounts.get(cursor.getInt(13));
					//Log.d(TAG,"Obtained account id: " + account);
					//if(account!=null) {
						data = new AppData(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(7), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6),
								cursor.getString(3), Timestamp.valueOf(cursor.getString(8)), Timestamp.valueOf(cursor.getString(9)), share, 
								owner);
					//}
			}
		}
		if(cursor!=null)
			cursor.close();
		return data;
	}
	
	public AppData getSharedSecretDataForApp(long local_id, int uid) {
		SQLiteDatabase db = this.getReadableDatabase();
		AppData data = null;
		List<Long> social_table_ids = new ArrayList<Long>();
		
		Cursor social_id_query = db.query(BINDINGS, new String[] {"social_id", "binding_type"}, "secret_id=?", new String[] {String.valueOf(local_id)}, null, null, null);
		if(social_id_query!=null && social_id_query.moveToFirst()) {
			do {
				social_table_ids.add(social_id_query.getLong(0));
			} while(social_id_query.moveToNext());
			social_id_query.close();
		} else {
			// Log.e(TAG,"Unable to get social ID data. Returning null");
			if(social_id_query!=null)
				social_id_query.close();
			return data;
		}
		
		/*String social_id=null;
		String social_name=null;
		int social_type=-1;*/
		List<SocialInfo> dataOwner = new ArrayList<SocialInfo>();
		for(Long i : social_table_ids) {
			Cursor social_query = db.query(SOCIAL_TABLE, new String[] {"social_type", "social_name", "social_id"}, "id=?", new String[] {String.valueOf(i)}, null, null, null);
			if(social_query!=null && social_query.moveToFirst()) {
				dataOwner.add(new SocialInfo(social_query.getString(2), social_query.getString(1), social_query.getInt(0)));
			}
			if(social_query!=null)
				social_query.close();
		}
		
		if(dataOwner.isEmpty()) {
			// Log.e(TAG,"Unable to get correct social data. Returning null");
			return data;
		}
		
		//PackageManager mgr = context.getPackageManager();
		//String pkg_name = mgr.getNameForUid(uid);
		Cursor cursor = db.query(SECRET_INFO,new String[] {"type","algorithm","value","description","sensitivity","created","expires","platform_id","platform_app_id"},"id=?",new String[] {String.valueOf(local_id)},null,null,null);
		if(cursor!=null && cursor.moveToFirst()) {
			//if((cursor.getInt(7) == PeerShareService.MobilePlatform.ANDROID) && (mgr.checkSignatures(pkg_name, cursor.getString(8)) == PackageManager.SIGNATURE_MATCH)) {
			if(verifyAppSignature(local_id, uid, false)) {
				data = new AppData(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), -1, social_id_query.getInt(1), cursor.getInt(4), 
					new String("undisclosed"), Timestamp.valueOf(cursor.getString(5)), Timestamp.valueOf(cursor.getString(6)),false,dataOwner);
			}
		}
		if(cursor!=null)
			cursor.close();
		return data;		
	}
	
	public ArrayList<AppData> getSharedSecretsDataForApp(int type, int uid) {
		//PackageManager mgr = context.getPackageManager();
		//String pkg_name = mgr.getNameForUid(uid);

		SQLiteDatabase db = this.getReadableDatabase();
		ArrayList<AppData> secrets = new ArrayList<AppData>();

		//printSocialInfoDebug();
		//printSecretInfoDebug();
		
		Cursor cursor = db.query(SECRET_INFO,new String[] {"type","algorithm","value","description","created","expires","id","sensitivity","platform_id","platform_app_id"},"type=?",new String[] {String.valueOf(type)},null,null,null);
		if(cursor!=null && cursor.moveToFirst()) {
			do {
				//if((cursor.getInt(8) == PeerShareService.MobilePlatform.ANDROID) && (mgr.checkSignatures(pkg_name, cursor.getString(9)) == PackageManager.SIGNATURE_MATCH)) {
				long secret_id = cursor.getLong(6);
				// Log.d(TAG,"Verifying secret id=" + secret_id);
				if(verifyAppSignature(secret_id, uid,false)) {
					//Log.d(TAG,"Verification successful");
					List<Long> social_table_ids = new ArrayList<Long>();
					Cursor social_id_query = db.query(BINDINGS, new String[] {"social_id","binding_type"}, "secret_id=?", new String[] {String.valueOf(secret_id)}, null, null, null);
					if(social_id_query!=null && social_id_query.moveToFirst()) {
						do {
							social_table_ids.add(social_id_query.getLong(0));
							//Log.d(TAG,"Adding social id: " + social_id_query.getLong(0));
						} while(social_id_query.moveToNext());
					} else {
						//Log.e(TAG,"Unable to get social ID data. Returning null");
						return null;
					}
					if(social_id_query!=null)
						social_id_query=null;

					/*String social_id=null;
					String social_name=null;
					int social_type=-1;*/
					List<SocialInfo> dataOwners = new ArrayList<SocialInfo>();
					for(Long i : social_table_ids) {
						//Log.d(TAG,"Querying for social ID: " + i);
						Cursor social_query = db.query(SOCIAL_TABLE, new String[] {"social_type", "social_name", "social_id"}, "id=?", new String[] {String.valueOf(i)}, null, null, null);
						if(social_query!=null && social_query.moveToFirst()) {
							dataOwners.add(new SocialInfo(social_query.getString(2), social_query.getString(1), social_query.getInt(0)));
						}
						if(social_query!=null)
							social_query.close();
					}

					if(dataOwners.isEmpty()) {
						//Log.e(TAG,"Unable to get correct social data. Returning null");
						return null;
					} else {
						//for(SocialInfo i : dataOwners)
							//Log.d(TAG,"Owner: " + i.getSocialName());
					}

					AppData data = new AppData(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), -1, /*social_id_query.getInt(1)*/ 2, cursor.getInt(7), new String("undisclosed"), 
							Timestamp.valueOf(cursor.getString(4)), Timestamp.valueOf(cursor.getString(5)), false, dataOwners);
					secrets.add(data);
				} else {
					Log.e(TAG,"Unsuccessful verification for object: " + secret_id);
				}
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		return secrets;		
	}
	
	public ArrayList<Long> deleteMySecrets(int type, int uid) {
		//PackageManager mgr = context.getPackageManager();
		//String pkg_name = mgr.getNameForUid(uid);
		ArrayList<Long> tmp = new ArrayList<Long>();
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		int i = 0;
		Cursor cursor = db.query(MY_SECRETS, new String[] {"object_id","platform_id","platform_app_id"}, "type=?", new String[] {String.valueOf(type)}, null, null, null);
		if(cursor != null && cursor.moveToFirst()) {
			do {
				//if((cursor.getInt(1) == PeerShareService.MobilePlatform.ANDROID) && (mgr.checkSignatures(pkg_name, cursor.getString(2)) == PackageManager.SIGNATURE_MATCH)) {
				if(verifyAppSignature(cursor.getLong(0), uid, true)) {
					tmp.add(Long.valueOf(cursor.getLong(0)));
					i++;
				}
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		
		int res = db.delete(MY_SECRETS, "type=?", new String[] {String.valueOf(type)});
		
		if(res == i)
			db.setTransactionSuccessful();
		
		db.endTransaction();
		return tmp;
	}
	
	public boolean removeDeletedSecrets(ArrayList<Long> ids) {
		SQLiteDatabase db = this.getWritableDatabase();
		
		Cursor cursor = db.query(MY_SECRETS, new String[] {"object_id"}, null, null, null, null, null);
		if(cursor!=null && cursor.moveToFirst()) {
			do {
				Long id = cursor.getLong(0);
				if(ids.contains(id)) {
					// Log.d(TAG,"Deleting object with ID=" + id + " it's old");
					if(db.delete(MY_SECRETS, "object_id=?", new String[] { String.valueOf(id) }) != 1)
						return false;
				}
					
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		return true;
	}
	
	// This method returns global object ID to delete. Argument is local object ID
	public long removeMySecret(long local_objectID, int uid) {
		long object_id = -1;
		if(verifyAppSignature(local_objectID, uid, true) == false) {
			return PeerShareService.APP_AUTH_ERROR;
		}
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		
		Cursor cursor = db.query(MY_SECRETS, new String[] {"object_id"}, "local_id=?", new String[] { String.valueOf(local_objectID) }, null, null, null, null);
		if(cursor != null && cursor.moveToFirst()) {
			object_id = cursor.getLong(0);
		}
		if(cursor!=null)
			cursor.close();
		
		int res = db.delete(MY_SECRETS, "local_id=?", new String[] {String.valueOf(local_objectID)});
		if(res == 1) {
			db.setTransactionSuccessful();
		}
		db.endTransaction();
		return object_id;
	}
	
	/*public boolean updateMySecretsFromApp(ArrayList<UISecret> secrets) {
		boolean ok = true;
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		for(int i=0; i<secrets.size();i++) {
			UISecret secret = secrets.get(i);
			ContentValues secret_update = new ContentValues();
			String policy = secret.getSecretPolicy();
			String policy_id = null;
			
			if((policy.compareTo(context.getString(R.string.friends_policy_text)) != 0) && (policy.compareTo(context.getString(R.string.only_me_policy_text)) != 0)) {
				
				// Log.d(TAG,"Querying for key name in database for policy: " + policy);
				Cursor cursor_1 = db.query(USER_INFO,new String[] {"key"},"value=?",new String[] {policy},null,null,null,null);
				if((cursor_1 == null) || (cursor_1.moveToFirst() == false)) {
					ok=false;
					break;
				}
				String name = new String(cursor_1.getString(0));
				String[] separated = name.split("\\.");
				String id_key = new String(separated[0] + "." + separated[1] + "." + "id");
				Cursor cursor_2 = db.query(USER_INFO,new String[] {"value"},"key=?",new String[] {id_key},null,null,null,null);
				if((cursor_2 == null) || (cursor_2.moveToFirst() == false)) {
					ok=false;
					break;
				}
				policy_id = new String(cursor_2.getString(0));
			} else 
				policy_id = policy;
			secret_update.put("policy", policy_id);
			long local_id = secret.getLocalID();
			int res = db.update(MY_SECRETS,secret_update,"local_id=?",new String[] { String.valueOf(local_id) });
			if(res != 1) {
				ok=false;
				break;
			}
		}
		
		// Log.d(TAG,"updateSecretsFromApp ok=" + ok);
		if(ok)
			db.setTransactionSuccessful();
		db.endTransaction();
		return ok;
	}*/
	
	/*public ArrayList<SecretDetails> getMySecretDetails(ArrayList<UISecret> secrets) {
		ArrayList<SecretDetails> tmp = new ArrayList<SecretDetails>();
		SQLiteDatabase db = this.getReadableDatabase();
		for(UISecret s : secrets) {
			Cursor cursor = db.query(MY_SECRETS,new String[] {"type", "algorithm", "value", "policy", "description", "SNtype","created","expires","specificity", "sensitivity", "binding_type","local_id","object_id","platform_id","platform_app_id"},"local_id=?",new String[] { String.valueOf(s.getLocalID()) },null,null,null,null);
			if(cursor != null && cursor.moveToFirst()) {
				// Log.i(TAG,"getSecretDetails() local ID=" + s.getLocalID() + " object ID=" + cursor.getLong(9));
				SecretDetails obj = new SecretDetails(cursor.getInt(0),cursor.getString(1),cursor.getString(2),cursor.getInt(10),cursor.getInt(8),cursor.getInt(9),cursor.getString(3),cursor.getString(4),cursor.getInt(5),Timestamp.valueOf(cursor.getString(6)),
						Timestamp.valueOf(cursor.getString(7)),cursor.getInt(11),cursor.getLong(12),cursor.getInt(13),cursor.getString(14));
				tmp.add(obj); 
			}
		}
		return tmp;
	}*/
	
	public SecretDetails getMySecret(long local_objectID) {
		SecretDetails tmp = null;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(MY_SECRETS,new String[] {"type", "algorithm", "value", "policy", "description", "SNtype", "created", "expires", "specificity", "sensitivity", "binding_type", "local_id", "object_id","platform_id","platform_app_id"},"local_id=?",new String[] { String.valueOf(local_objectID) }, null, null, null, null);
		if(cursor != null && cursor.moveToFirst()) {
			tmp = new SecretDetails(cursor.getInt(0),cursor.getString(1),cursor.getString(2),cursor.getInt(10),cursor.getInt(8),cursor.getInt(9),cursor.getString(3),cursor.getString(4),cursor.getInt(5),Timestamp.valueOf(cursor.getString(6)),
					Timestamp.valueOf(cursor.getString(7)),cursor.getInt(11),cursor.getLong(12),cursor.getInt(13),cursor.getString(14));
		}
		if(cursor!=null)
			cursor.close();
		return tmp;
	}
	
	public ArrayList<AppData> getMyTypeSecretsForApp(int type) {
		SQLiteDatabase db = this.getReadableDatabase();
		ArrayList<AppData> tmp = new ArrayList<AppData>();
		Map<Integer,SocialNetworkAccount> accounts = getRegisteredSocialNetworkAccounts();
		
		Cursor cursor = db.query(MY_SECRETS,new String[] {"type", "algorithm", "value", "description", "sensitivity", "specificity", "binding_type", "policy", "created", "expires", "local_id", "share", "SNtype"},"type=?",new String[] { String.valueOf(type) }, null, null, null, null);
		if(cursor != null && cursor.moveToFirst()) {
			do {
				boolean share = false;
				if(cursor.getInt(7) == 1)
					share=true;
				List<SocialInfo> owner = new ArrayList<SocialInfo>();
				for(Integer i : accounts.keySet()) {
					//Log.d(TAG,"Registered account: " + i + " type: " + cursor.getInt(13));
					SocialNetworkAccount account = accounts.get(i);
					owner.add(new SocialInfo(account.getUserID(),account.getUsername(),account.getSocialNetworkID()));
				}
				//SocialNetworkAccount account = accounts.get(cursor.getInt(12));
				//if(account!=null) {
					AppData secret = new AppData(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(5),cursor.getInt(6), cursor.getInt(4), 
						cursor.getString(7), Timestamp.valueOf(cursor.getString(8)), Timestamp.valueOf(cursor.getString(9)), share,
						owner);
					tmp.add(secret);
				//}
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		return tmp;
	}
	
	// This method returns list of local object IDs for given type
	public ArrayList<Long> getMyTypeSecretIDsForApp(int type, int uid) {
		PackageManager mgr = context.getPackageManager();
		String package_name = mgr.getNameForUid(uid);
		PackageInfo info = null;
		try {
			info = mgr.getPackageInfo(package_name, PackageManager.GET_SIGNATURES);
		} catch(NameNotFoundException e) {
			// Log.e(TAG,"Wrong app trying to call the service. Exiting");
			return null;
		}
		Signature[] signs = info.signatures;
		if(signs == null) {
			// Log.e(TAG,"Application not signed. Denying access to the service. Exiting");
			return null;
		}
		SQLiteDatabase db = this.getReadableDatabase();
		ArrayList<Long> tmp = new ArrayList<Long>();
		Cursor cursor = db.query(MY_SECRETS,new String[] {"local_id","platform_id","platform_app_id"},"type=?",new String[] { String.valueOf(type) }, null, null, null, null);
		if(cursor != null && cursor.moveToFirst()) {
			do {
				String[] items = cursor.getString(2).split(";");
				if(items.length == 2) {
					String stored_signature = items[1];
					if((cursor.getInt(1) == PeerShareService.MobilePlatform.ANDROID) && (stored_signature.equals(signs[0].toCharsString())))
						tmp.add(cursor.getLong(0));
				}
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		return tmp;		
	}
	
	// This method returns list of local object IDs of secrets shared with me 
	public ArrayList<Long> getSharedTypeSecretIDsForApp(int type, int uid) {
		PackageManager mgr = context.getPackageManager();
		String package_name = mgr.getNameForUid(uid);
		PackageInfo info = null;
		try {
			info = mgr.getPackageInfo(package_name, PackageManager.GET_SIGNATURES);
		} catch(NameNotFoundException e) {
			// Log.e(TAG,"Wrong app trying to call the service. Exiting");
			return null;
		}
		Signature[] signs = info.signatures;
		if(signs == null) {
			// Log.e(TAG,"Application not signed. Denying access to the service. Exiting");
			return null;
		}		
		
		SQLiteDatabase db = this.getReadableDatabase();
		ArrayList<Long> tmp = new ArrayList<Long>();
		Cursor cursor = db.query(SECRET_INFO,new String[] {"id","platform_id","platform_app_id"},"type=?",new String[] { String.valueOf(type) }, null, null, null, null);
		if(cursor != null && cursor.moveToFirst()) {
			do {
				String[] items = cursor.getString(2).split(";");
				if(items.length == 2) {
					String stored_signature = items[1];
					if((cursor.getInt(1) == PeerShareService.MobilePlatform.ANDROID) && (stored_signature.equals(signs[0].toCharsString())))
						tmp.add(cursor.getLong(0));
				}
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		return tmp;		
	}
	
	/*public ArrayList<UISecret> getMySecrets() {
		ArrayList<UISecret> tmp = new ArrayList<UISecret>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(MY_SECRETS,new String[] {"description", "policy", "share", "local_id"},null,null,null,null,null,null);
		if(cursor != null && cursor.moveToFirst()) {
			do {
				UISecret obj = new UISecret(cursor.getLong(3),cursor.getString(0),cursor.getString(1));
				tmp.add(obj);
			} while(cursor.moveToNext());
		}
		return tmp;
	}*/
	
	/*public ArrayList<SecretBinding> getSharedSecrets() {
		ArrayList<SecretBinding> tmp = new ArrayList<SecretBinding>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor secret_cursor = db.query(SECRET_INFO,new String[] {"description","id"},null,null,null,null,null);
		if(secret_cursor!=null && secret_cursor.moveToFirst()) {
			do {
				Cursor binding_cursor = db.query(BINDINGS,new String[] {"social_id","binding_type"},"secret_id=? AND obsolete=0",new String[] { secret_cursor.getString(1) },null,null,null);
				if(binding_cursor != null && binding_cursor.moveToFirst()) {
					Cursor social_cursor = db.query(SOCIAL_TABLE,new String[] {"social_type", "social_name"},"id=?",new String[] {binding_cursor.getString(0)},null,null,null,null);
					if(social_cursor != null && social_cursor.moveToFirst()) {
						SecretBinding secret = new SecretBinding(social_cursor.getString(1), social_cursor.getInt(0), secret_cursor.getString(0), binding_cursor.getInt(1));
						tmp.add(secret);
					}
				}
			} while(secret_cursor.moveToNext());
		} else
			// Log.e(TAG,"Unable to get secrets from the secret info table");
		return tmp;
	}*/
	
	public ArrayList<SecretDetails> getUploadableSecrets() {
		
		//this.printMySecretsDebug();
		
		ArrayList<SecretDetails> tmp = new ArrayList<SecretDetails>();
		SQLiteDatabase db = this.getReadableDatabase();
		// object_id == -1, this means that data is stored only locally, and doesn't have global object_id
		Cursor cursor = db.query(MY_SECRETS,new String[] {"type","algorithm","value","policy","description","SNtype","created","expires", "specificity", "sensitivity", "binding_type", "local_id", "object_id","platform_id","platform_app_id"},"share=1 AND object_id=-1",null,null,null,null,null);
		if(cursor != null && cursor.moveToFirst()) {
			// Log.d(TAG,"getUploadableSecrets amount = " + cursor.getCount());
			do {
				SecretDetails obj = new SecretDetails(cursor.getInt(0),cursor.getString(1),cursor.getString(2),cursor.getInt(10),cursor.getInt(8),cursor.getInt(9),cursor.getString(3),cursor.getString(4),cursor.getInt(5),
						Timestamp.valueOf(cursor.getString(6)),Timestamp.valueOf(cursor.getString(7)),cursor.getInt(11),cursor.getLong(12),cursor.getInt(13),cursor.getString(14));
				tmp.add(obj);
				ContentValues updates = new ContentValues();
				updates.put("object_id", -2);
				db.update(MY_SECRETS, updates, "local_id=?", new String[] { cursor.getString(11) });
			} while(cursor.moveToNext());
		} else {
			// Log.d(TAG,"No secrets to uplad. Cursor=null");
		}
		if(cursor!=null)
			cursor.close();
		return tmp;		
	}
	
	public ArrayList<Long> getSecretsForRefresh() {
		ArrayList<Long> tmp = new ArrayList<Long>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(MY_SECRETS,new String[] {"object_id"},"share=1 AND object_id!=-1",null,null,null,null,null);
		if(cursor != null && cursor.moveToFirst()) {
			do {
				tmp.add(cursor.getLong(0));
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		return tmp;
	}
	
	public boolean setObjectIDs(HashMap<Long,Long> objects) {
		boolean successful = true;
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		Iterator<Long> it=objects.keySet().iterator();
		while(it.hasNext()) {
			Long local_id = it.next();
			Long object_id = objects.get(local_id);
			Cursor cursor = db.query(MY_SECRETS, new String[] {"local_id"}, "object_id=?", new String[] {String.valueOf(object_id)}, null, null, null);
			if(cursor!=null && cursor.moveToFirst()) {
				db.delete(MY_SECRETS, "local_id=?", new String[] {cursor.getString(0)});
			}
			if(cursor!=null)
				cursor.close();
			
			ContentValues data = new ContentValues();
			data.put("object_id", objects.get(local_id));
			// Log.d(TAG,"setObjectIDs local ID=" + local_id + " object ID=" + objects.get(local_id));
			if(db.update(MY_SECRETS, data, "local_id=?", new String[] { String.valueOf(local_id)}) != 1) {
				// Log.e(TAG,"setObjectIDs error. Update returns error for " + local_id);
				successful = false;
				break;
			}
		}
		if(successful)
			db.setTransactionSuccessful();
		db.endTransaction();
		
		//printMySecretsDebug();
		// Log.d(TAG,"End of upload operation");
		
		return successful;
	}
	
	public boolean isThisMySecret(int type, String value, String algorithm, String description) {
		boolean ok = false;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(MY_SECRETS, new String[] {"type","algorithm","value","description"}, "type=? AND algorithm=? AND value=? AND description=?", 
				new String[] {String.valueOf(type),algorithm,value,description}, null, null, null);
		if(cursor!=null && cursor.moveToFirst()) {
			if(cursor.getInt(0) == type && cursor.getString(1).equals(algorithm) && cursor.getString(2).equals(value) && cursor.getString(3).equals(description))
				ok = true;
		}
		if(cursor!=null)
			cursor.close();
		return ok;
	}
	
	public boolean storePendingAction(String type, String url, String body) {
		boolean ok = false;
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues data = new ContentValues();
		data.put("type", type);
		data.put("body", body);
		data.put("url", url);
		if(db.insert(PENDING_ACTIONS, null, data) != -1)
			ok=true;
		return ok;
	}
	
	public ArrayList<PendingRequest> getPendingActions() {
		ArrayList<PendingRequest> tmp = new ArrayList<PendingRequest>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(PENDING_ACTIONS,new String[] {"type", "body", "url", "id"},null,null,null,null,null,null);
		if(cursor!=null && cursor.moveToFirst()) {
			do {
				PendingRequest request = new PendingRequest(cursor.getLong(3), cursor.getString(2), cursor.getString(0), cursor.getString(1));
				tmp.add(request);
			} while(cursor.moveToNext());
		}
		if(cursor!=null)
			cursor.close();
		return tmp;
	}
	
	public boolean deletePendingRequest(long id) {
		boolean ok = false;
		SQLiteDatabase db = this.getWritableDatabase();
		if( (db.delete(PENDING_ACTIONS, "id=?", new String[] { String.valueOf(id) })) == 1)
			ok = true;
		return ok;
	}
	
	public long getNextScheduledRefresh() {
		long ts = -1;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(USER_INFO,new String[] {"value"},"key=?",new String[] {"nextScheduledRefresh"},null,null,null,null);
		if(cursor!=null && cursor.moveToFirst())
			ts = cursor.getLong(0);
		if(cursor!=null)
			cursor.close();
		return ts;
	}
	
	public long getTimeSinceLastFetch() {
		long ts = -1;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(USER_INFO,new String[] {"value"},"key=?",new String[] {"last_server_download"},null,null,null,null);
		if(cursor!=null && cursor.moveToFirst())
			ts = cursor.getLong(0);
		if(cursor!=null)
			cursor.close();
		return ts;		
	}
	
	protected String generatePseudonym(String mac, String name) {
		String pseudo="";
		try {
			String text = mac.toLowerCase(Locale.getDefault())+name;
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.reset();
			md.update(text.getBytes("UTF-8"));
			pseudo =  byteToHex(md.digest());
		} catch(NoSuchAlgorithmException e) {
			// Log.e(TAG,"generatePseudonym" + e.getMessage());
		} catch(UnsupportedEncodingException e) {
			// Log.e(TAG,"generatePseudonym " + e.getMessage());
		}
		return pseudo;
	}
	
	public PersonInfo whoIs(AppData data) {
		//SQLiteDatabase db = this.getReadableDatabase();
		//Cursor cursor = db.query(SECRET_INFO,new String[] {"id"},"type=? AND algorithm=? AND value=?",new String[] {String.valueOf(data.getSecretType()),data.getSecretAlgorithm(),data.getSecretValue()},null,null,null,null);
		//if(cursor!=null && cursor.moveToFirst())
		//	ts = cursor.getLong(0);
		PersonInfo tmp = new PersonInfo(); //new PersonInfo(pid, name, network, nid);
		return tmp;
	}
	
	protected String byteToHex(byte[] array) {
		StringBuffer hexString = new StringBuffer();
		for (byte b : array) {
			int intVal = b & 0xff;
			if (intVal < 0x10)
				hexString.append("0");
			hexString.append(Integer.toHexString(intVal));
		}
		return hexString.toString();    
	}
	
	private boolean verifyAppSignature(long local_id, int uid, boolean my_secret) {
		SQLiteDatabase db = this.getReadableDatabase();
		boolean ret = false;
		Cursor cursor = null;
		if(my_secret)
			cursor = db.query(MY_SECRETS, new String[] {"platform_id","platform_app_id"}, "local_id=?", new String[] {String.valueOf(local_id)}, null, null, null);
		else
			cursor = db.query(SECRET_INFO, new String[] {"platform_id","platform_app_id"}, "id=?", new String[] {String.valueOf(local_id)}, null, null, null);
		if(cursor!=null && cursor.moveToFirst()) {
			int platform_id = cursor.getInt(0);
			
			String platform_app_id = cursor.getString(1);
			String[] items = platform_app_id.split(";");
			if(items.length != 2)
				return ret;
			String package_name = items[0];
			String stored_signature = items[1];
			
			PackageManager manager = this.context.getPackageManager();
			PackageInfo info = null;
			try {
				info = manager.getPackageInfo(manager.getNameForUid(uid), PackageManager.GET_SIGNATURES);
			} catch(NameNotFoundException e) {
				// Log.e(TAG,"Package info not found for the given name. Signature verification failed");
				return ret;
			}
			Signature[] signs = info.signatures;
			if(signs == null)
				return ret;
						
			if((info.packageName.equals(package_name)) &&
					/*(platform_id == PeerShareService.MobilePlatform.ANDROID) && */
					(stored_signature.equals(signs[0].toCharsString()))) {
				ret = true;
			} else {
				// Log.e(TAG,"Different package signature");
			}
		}
		if(cursor!=null)
			cursor.close();
		return ret;
	}

}
