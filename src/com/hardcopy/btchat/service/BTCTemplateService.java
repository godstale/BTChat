/*
 * Copyright (C) 2014 Bluetooth Connection Template
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hardcopy.btchat.service;

import java.util.ArrayList;
import java.util.Timer;

import com.hardcopy.btchat.bluetooth.*;
import com.hardcopy.btchat.contents.CommandParser;
import com.hardcopy.btchat.utils.AppSettings;
import com.hardcopy.btchat.utils.Constants;
import com.hardcopy.btchat.utils.Logs;
import com.hardcopy.btchat.http.HttpAsyncTask;
import com.hardcopy.btchat.http.HttpInterface;
import com.hardcopy.btchat.http.HttpListener;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;


public class BTCTemplateService extends Service {
	private static final String TAG = "LLService";
	
	// Context, System
	private Context mContext = null;
	private static Handler mActivityHandler = null;
	private ServiceHandler mServiceHandler = new ServiceHandler();
	private final IBinder mBinder = new ServiceBinder();
	
	// Bluetooth
	private BluetoothAdapter mBluetoothAdapter = null;		// local Bluetooth adapter managed by Android Framework
	private BluetoothManager mBtManager = null;
	private ConnectionInfo mConnectionInfo = null;		// Remembers connection info when BT connection is made 
	private CommandParser mCommandParser = null;
	
	private TransactionBuilder mTransactionBuilder = null;
	private TransactionReceiver mTransactionReceiver = null;
	
	// Auto-refresh timer
	private Timer mRefreshTimer = null;
	private Timer mDeleteTimer = null;
    
	
	/*****************************************************
	 *	Overrided methods
	 ******************************************************/
	@Override
	public void onCreate() {
		Logs.d(TAG, "# Service - onCreate() starts here");
		
		mContext = getApplicationContext();
		initialize();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Logs.d(TAG, "# Service - onStartCommand() starts here");
		
		// If service returns START_STICKY, android restarts service automatically after forced close.
		// At this time, onStartCommand() method in service must handle null intent.
		return Service.START_STICKY;
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig){
		// This prevents reload after configuration changes
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Logs.d(TAG, "# Service - onBind()");
		return mBinder;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		Logs.d(TAG, "# Service - onUnbind()");
		return true;
	}
	
	@Override
	public void onDestroy() {
		Logs.d(TAG, "# Service - onDestroy()");
		finalizeService();
	}
	
	@Override
	public void onLowMemory (){
		Logs.d(TAG, "# Service - onLowMemory()");
		// onDestroy is not always called when applications are finished by Android system.
		finalizeService();
	}

	
	/*****************************************************
	 *	Private methods
	 ******************************************************/
	private void initialize() {
		Logs.d(TAG, "# Service : initialize ---");
		
		AppSettings.initializeAppSettings(mContext);
		startServiceMonitoring();
		
		// Make instances
		mConnectionInfo = ConnectionInfo.getInstance(mContext);
		mCommandParser = new CommandParser();
		
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			return;
		}
		
		if (!mBluetoothAdapter.isEnabled()) {
			// BT is not on, need to turn on manually.
			// Activity will do this.
		} else {
			if(mBtManager == null) {
				setupBT();
			}
		}
	}
	
	/**
	 * Send message to device.
	 * @param message		message to send
	 */
	private void sendMessageToDevice(String message) {
		if(message == null || message.length() < 1)
			return;
		
		TransactionBuilder.Transaction transaction = mTransactionBuilder.makeTransaction();
		transaction.begin();
		transaction.setMessage(message);
		transaction.settingFinished();
		transaction.sendTransaction();
	}
	
	/**
	 * 
	 */
	
	
	/*****************************************************
	 *	Public methods
	 ******************************************************/
	public void finalizeService() {
		Logs.d(TAG, "# Service : finalize ---");
		
		// Stop the bluetooth session
		mBluetoothAdapter = null;
		if (mBtManager != null)
			mBtManager.stop();
		mBtManager = null;
		
		// Stop the timer
		if(mRefreshTimer != null) {
			mRefreshTimer.cancel();
			mRefreshTimer = null;
		}
		if(mDeleteTimer != null) {
			mDeleteTimer.cancel();
			mDeleteTimer = null;
		}
		
	}
	
	/**
	 * Setting up bluetooth connection
	 * @param h
	 */
	public void setupService(Handler h) {
		mActivityHandler = h;
		
		// Double check BT manager instance
		if(mBtManager == null)
			setupBT();
		
		// Initialize transaction builder & receiver
		if(mTransactionBuilder == null)
			mTransactionBuilder = new TransactionBuilder(mBtManager, mActivityHandler);
		if(mTransactionReceiver == null)
			mTransactionReceiver = new TransactionReceiver(mActivityHandler);
		
		// If ConnectionInfo holds previous connection info,
		// try to connect using it.
		if(mConnectionInfo.getDeviceAddress() != null && mConnectionInfo.getDeviceName() != null) {
			connectDevice(mConnectionInfo.getDeviceAddress());
		} 
		// or wait in listening mode
		else {
			if (mBtManager.getState() == BluetoothManager.STATE_NONE) {
				// Start the bluetooth service
				mBtManager.start();
			}
		}
	}
	
    /**
     * Setup and initialize BT manager
     */
	public void setupBT() {
        Logs.d(TAG, "Service - setupBT()");

        // Initialize the BluetoothManager to perform bluetooth connections
        if(mBtManager == null)
        	mBtManager = new BluetoothManager(this, mServiceHandler);
    }
	
    /**
     * Check bluetooth is enabled or not.
     */
	public boolean isBluetoothEnabled() {
		if(mBluetoothAdapter==null) {
			Log.e(TAG, "# Service - cannot find bluetooth adapter. Restart app.");
			return false;
		}
		return mBluetoothAdapter.isEnabled();
	}
	
	/**
	 * Get scan mode
	 */
	public int getBluetoothScanMode() {
		int scanMode = -1;
		if(mBluetoothAdapter != null)
			scanMode = mBluetoothAdapter.getScanMode();
		
		return scanMode;
	}

    /**
     * Initiate a connection to a remote device.
     * @param address  Device's MAC address to connect
     */
	public void connectDevice(String address) {
		Logs.d(TAG, "Service - connect to " + address);
		
		// Get the BluetoothDevice object
		if(mBluetoothAdapter != null) {
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
			
			if(device != null && mBtManager != null) {
				mBtManager.connect(device);
			}
		}
	}
	
    /**
     * Connect to a remote device.
     * @param device  The BluetoothDevice to connect
     */
	public void connectDevice(BluetoothDevice device) {
		if(device != null && mBtManager != null) {
			mBtManager.connect(device);
		}
	}

	/**
	 * Get connected device name
	 */
	public String getDeviceName() {
		return mConnectionInfo.getDeviceName();
	}

	/**
	 * Send message to remote device using Bluetooth
	 */
	public void sendMessageToRemote(String message) {
		sendMessageToDevice(message);
	}
	
	/**
	 * Start service monitoring. Service monitoring prevents
	 * unintended close of service.
	 */
	public void startServiceMonitoring() {
		if(AppSettings.getBgService()) {
			ServiceMonitoring.startMonitoring(mContext);
		} else {
			ServiceMonitoring.stopMonitoring(mContext);
		}
	}
	
	
	
	/*****************************************************
	 *	Handler, Listener, Timer, Sub classes
	 ******************************************************/
	public class ServiceBinder extends Binder {
		public BTCTemplateService getService() {
			return BTCTemplateService.this;
		}
	}
	
    /**
     * Receives messages from bluetooth manager
     */
	class ServiceHandler extends Handler
	{
		@Override
		public void handleMessage(Message msg) {
			
			switch(msg.what) {
			// Bluetooth state changed
			case BluetoothManager.MESSAGE_STATE_CHANGE:
				// Bluetooth state Changed
				Logs.d(TAG, "Service - MESSAGE_STATE_CHANGE: " + msg.arg1);
				
				switch (msg.arg1) {
				case BluetoothManager.STATE_NONE:
					mActivityHandler.obtainMessage(Constants.MESSAGE_BT_STATE_INITIALIZED).sendToTarget();
					if(mRefreshTimer != null) {
						mRefreshTimer.cancel();
						mRefreshTimer = null;
					}
					break;
					
				case BluetoothManager.STATE_LISTEN:
					mActivityHandler.obtainMessage(Constants.MESSAGE_BT_STATE_LISTENING).sendToTarget();
					break;
					
				case BluetoothManager.STATE_CONNECTING:
					mActivityHandler.obtainMessage(Constants.MESSAGE_BT_STATE_CONNECTING).sendToTarget();
					break;
					
				case BluetoothManager.STATE_CONNECTED:
					mActivityHandler.obtainMessage(Constants.MESSAGE_BT_STATE_CONNECTED).sendToTarget();
					break;
				}
				break;

			// If you want to send data to remote
			case BluetoothManager.MESSAGE_WRITE:
				Logs.d(TAG, "Service - MESSAGE_WRITE: ");
				break;

			// Received packets from remote
			case BluetoothManager.MESSAGE_READ:
				Logs.d(TAG, "Service - MESSAGE_READ: ");
				
				byte[] readBuf = (byte[]) msg.obj;
				int readCount = msg.arg1;
				// send bytes in the buffer to activity
				if(msg.arg1 > 0) {
					String strMsg = new String(readBuf, 0, msg.arg1);
					mActivityHandler.obtainMessage(Constants.MESSAGE_READ_CHAT_DATA, strMsg)
							.sendToTarget();
					int command = mCommandParser.setString(strMsg);
					if(command == CommandParser.COMMAND_THINGSPEAK) {
						String parameters = mCommandParser.getParameterString();
						StringBuilder requestUrl = new StringBuilder("http://184.106.153.149/update?");
						if(parameters != null && parameters.length() > 0)
							requestUrl.append(parameters);
						
						//Logs.d("# Find thingspeak command. URL = "+requestUrl);
						
						HttpAsyncTask task = new HttpAsyncTask(mHTTPListener, 0, requestUrl.toString(), HttpInterface.REQUEST_TYPE_GET);
						task.execute();
						mCommandParser.resetParser();
					}
				}
				break;
				
			case BluetoothManager.MESSAGE_DEVICE_NAME:
				Logs.d(TAG, "Service - MESSAGE_DEVICE_NAME: ");
				
				// save connected device's name and notify using toast
				String deviceAddress = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_ADDRESS);
				String deviceName = msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_DEVICE_NAME);
				
				if(deviceName != null && deviceAddress != null) {
					// Remember device's address and name
					mConnectionInfo.setDeviceAddress(deviceAddress);
					mConnectionInfo.setDeviceName(deviceName);
					
					Toast.makeText(getApplicationContext(), 
							"Connected to " + deviceName, Toast.LENGTH_SHORT).show();
				}
				break;
				
			case BluetoothManager.MESSAGE_TOAST:
				Logs.d(TAG, "Service - MESSAGE_TOAST: ");
				
				Toast.makeText(getApplicationContext(), 
						msg.getData().getString(Constants.SERVICE_HANDLER_MSG_KEY_TOAST), 
						Toast.LENGTH_SHORT).show();
				break;
				
			}	// End of switch(msg.what)
			
			super.handleMessage(msg);
		}
	}	// End of class MainHandler
	
	
	// HTTP Listener
	private HttpListener mHTTPListener = new HttpListener() {
		@Override
		public void OnReceiveHttpResponse(int type, String strResult, int resultCode) 
		{
			if(strResult != null && strResult.length() > 0 
					&& resultCode == HttpInterface.MSG_HTTP_RESULT_CODE_OK){
				Logs.d(TAG, "# Result code = "+strResult);
			}
		}	// End of OnReceiveHttpRequestResult()
		
		@Override
		public void OnReceiveFileResponse(int type, String id, String filepath, String url, int resultCode) {
			// Disabled
		}
	};
	
}
