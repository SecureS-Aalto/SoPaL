package org.sesy.socialauth;

import org.sesy.tetheringapp.R;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;

public class AuthenticationActivity extends Activity {
	
	private static final String TAG = "AuthenticationActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_authentication);
		
		ActionBar mActionBar = getActionBar();
		mActionBar.setDisplayShowHomeEnabled(false);
		mActionBar.setDisplayShowTitleEnabled(false);
		LayoutInflater mInflater = LayoutInflater.from(this);
		View mCustomView = mInflater.inflate(R.layout.top_bar, null);
		mActionBar.setCustomView(mCustomView);
		mActionBar.setDisplayShowCustomEnabled(true);
		mActionBar.setHomeButtonEnabled(false);
		
		Intent i = getIntent();
		if(i!=null) {
			View v1 = findViewById(R.id.facebookButton);
			View v2 = findViewById(R.id.facebookButton2);
			View v3 = findViewById(R.id.linkedinButton);
			View v4 = findViewById(R.id.linkedinButton2);
			if(!i.hasExtra("Facebook")) {
				//Log.d(TAG,"No facebook data");			
				if(v1!=null)
					v1.setEnabled(false);
				if(v2!=null)
					v2.setEnabled(true);
			} else {
				if(v1!=null)
					v1.setEnabled(true);
				if(v2!=null)
					v2.setEnabled(false);
			}
			if(!i.hasExtra("LinkedIn")) {
				//Log.d(TAG,"No linkedin data");		
				if(v3!=null)
					v3.setEnabled(false);
				if(v4!=null)
					v4.setEnabled(true);
			} else {
				if(v3!=null)
					v3.setEnabled(true);
				if(v4!=null)
					v4.setEnabled(false);				
			}
		}
	}
	
	@Override
	public void onBackPressed() {
		View v1 = findViewById(R.id.facebookButton);
		View v2 = findViewById(R.id.linkedinButton);
		if(v1.isEnabled() && v2.isEnabled()) {
			return;
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.authentication, menu);
		return true;
	}
	
	public void onClick(View v) {
		final int id = v.getId();
		
		switch (id) {
			case R.id.linkedinButton:
				// Authenticate with linkedin
				//Log.d("Custom-UI", "Linkedin login");
				Intent data = new Intent(SocialAuthManager.AUTH_CHOICE_COMPLETED);
				data.putExtra("SN", SocialAuthManager.LINKEDIN);
				sendBroadcast(data);
				finish();
				break;
			case R.id.facebookButton:
				// Authenticate with fb
				//Log.d("Custom-UI", "FB login");
				Intent data1 = new Intent(SocialAuthManager.AUTH_CHOICE_COMPLETED);
				data1.putExtra("SN", SocialAuthManager.FACEBOOK);
				sendBroadcast(data1);
				finish();
				break;
			case R.id.facebookButton2:
				Intent data2 = new Intent(SocialAuthManager.SIGNOUT_CHOICE_COMPLETED);
				data2.putExtra("SN", SocialAuthManager.FACEBOOK);
				sendBroadcast(data2);
				finish();
				break;
			case R.id.linkedinButton2:
				Intent data3 = new Intent(SocialAuthManager.SIGNOUT_CHOICE_COMPLETED);
				data3.putExtra("SN", SocialAuthManager.LINKEDIN);
				sendBroadcast(data3);
				finish();
				break;
		}
	}

}
