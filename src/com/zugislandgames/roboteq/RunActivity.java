package com.zugislandgames.roboteq;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.zugislandgames.roboteq.R.id;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView.OnEditorActionListener;
import android.view.KeyEvent;

public class RunActivity extends Activity implements View.OnClickListener,  OnSeekBarChangeListener{

	private Button connectUSB;
	private TextView outputTxt;
    private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    ArrayAdapter<String> dataAdapter;
    
    private HashMap<String, UsbDevice> deviceList;
	private Builder alertDialog;
	private usbCommThread usbCommThread;

	private static int TIMEOUT = 0;
	//UI components
	private SeekBar sbM1;
	private SeekBar sbM2;
	private Button m1StopBtn;
	private Button m2StopBtn;
	private CheckBox joinCheckBox;
	private TextView cmdM1Txt;
	private TextView cmdM2Txt;
	//
	private Button incM1Btn;
	private Button incXM1Btn;
	private Button decM1Btn;
	private Button decXM1Btn;
	private Button incM2Btn;
	private Button incXM2Btn;
	private Button decM2Btn;
	private Button decXM2Btn;
	private CheckBox joinCheckbox;
	private Button sendCmdBtn;
	//
	private static int SEEKBAR_OFFSET = 1000;
	//
	private Handler uiHandler = new UIHandler();//allows communication between UI thread and USBCommThread
	private ArrayAdapter<String> adapter;//adapter
	public ArrayList<String> dataValues;//values read from usb input
	private GridView consoleTxt;
	private EditText sendCmdTxt;
	
	/*
	 * USB connection permission receiver from : http://developer.android.com/guide/topics/connectivity/usb/accessory.html
	 */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
   	 
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                	UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up accessory communication
                        	//outputTxt.setText("permission granted for accessory"  + device);
                        	connectUSB(device);
                        }
                    }
                    else {
                       //outputTxt.setText( "permission denied for accessory " + device);
                    }
                }
            }
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_run);
		
		connectUSB = (Button)findViewById(R.id.usbconnectBtn);
		connectUSB.setOnClickListener(this);
		
    	//join checkbox
		joinCheckbox = (CheckBox) findViewById(R.id.joinCheckBox);
		joinCheckbox.setOnClickListener(this);
		//seekbar M1
    	sbM1 = (SeekBar) findViewById(R.id.seekBarM1);
    	sbM1.setOnSeekBarChangeListener(this);
    	//seekbar M2
    	sbM2 = (SeekBar) findViewById(R.id.seekBarM2);
    	sbM2.setOnSeekBarChangeListener(this);
    	//stop buttons m1 & m2
    	m1StopBtn = (Button) findViewById(R.id.m1StopBtn);//M1
    	m1StopBtn.setOnClickListener(this);
		m2StopBtn = (Button) findViewById(R.id.m2StopBtn);//M2
		
		//M1 command inc/dec buttons
		incM1Btn = (Button) findViewById(R.id.incM1Btn);
		incM1Btn.setOnClickListener(this);
		incXM1Btn = (Button) findViewById(R.id.incXM1Btn);
		incXM1Btn.setOnClickListener(this);
		//
		decM1Btn = (Button) findViewById(R.id.decM1Btn);
		decM1Btn.setOnClickListener(this);
		decXM1Btn = (Button) findViewById(R.id.decXM1Btn);
		decXM1Btn.setOnClickListener(this);
		
		//M2 command inc/dec buttons
		incM2Btn = (Button) findViewById(R.id.incM2Btn);
		incM2Btn.setOnClickListener(this);
		incXM2Btn = (Button) findViewById(R.id.incXM2Btn);
		incXM2Btn.setOnClickListener(this);
		//
		decM2Btn = (Button) findViewById(R.id.decM2Btn);
		decM2Btn.setOnClickListener(this);
		decXM2Btn = (Button) findViewById(R.id.decXM2Btn);
		decXM2Btn.setOnClickListener(this);
		 		
		//join checkbox
		joinCheckBox = (CheckBox) findViewById(R.id.joinCheckBox);//join checkbox
		
		//send button
		sendCmdBtn = (Button) findViewById(R.id.sendcmdbtn);	
		sendCmdBtn.setOnClickListener(this);
		
		m2StopBtn.setOnClickListener(this);
		
		//
		dataValues = new ArrayList<String>(1);
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataValues); 
		consoleTxt = (GridView)findViewById(R.id.consoletxt);
		consoleTxt.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        consoleTxt.invalidateViews();
        //
        sendCmdTxt = (EditText)findViewById(R.id.sendcmdtxt);
        sendCmdTxt.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                	usbCommThread.sendCommand((sendCmdTxt.getText().toString().trim() + "\n\r"));
                    handled = true;
                }
                return handled;
            }
        });        
		
		outputTxt = (TextView) findViewById(R.id.statustxt);
		//outputTxt.set
		cmdM1Txt = (TextView) findViewById(R.id.cmdM1);
		cmdM2Txt = (TextView) findViewById(R.id.cmdM2);
		
        alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Select USB Device");

		
        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        
        usbCommThread = new usbCommThread();
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.run, menu);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 * Button onclick handler
	 */
	@Override
	public void onClick(View v) {				
		
		switch(v.getId()){
			case R.id.usbconnectBtn:
		    		deviceList = getUSBDeviceList();
		    		//Get device names
		    		List<String> devices =  new ArrayList<String>();
		    		Set<String> deviceKey = deviceList.keySet();
		    		for(String usb : deviceKey){    			
		    			devices.add(usb);
		    		}    		    		
		    		addItemsToUSBDeviceAdapter(devices);    		
		    		showUSBDeviceDropDown();		    					
	    		break;
				
			case R.id.m1StopBtn:
				sbM1.setProgress(SEEKBAR_OFFSET);
				updateM1(updateM1Progress(0) - SEEKBAR_OFFSET);				
				break;
				
			case R.id.m2StopBtn:
				sbM2.setProgress(SEEKBAR_OFFSET);
				updateM2(updateM2Progress(0) - SEEKBAR_OFFSET);
				break;
				
			case R.id.incM1Btn:	
				//outputTxt.setText("Data:" + usbCommThread.getStr().toString());
				updateM1(updateM1Progress(1) - SEEKBAR_OFFSET);	    			
				break;
				
			case R.id.incXM1Btn:				
				updateM1(updateM1Progress(10) - SEEKBAR_OFFSET);
				break;
				
			case R.id.decM1Btn:
				updateM1(updateM1Progress(-1) - SEEKBAR_OFFSET);
				break;
				
			case R.id.decXM1Btn:
				updateM1(updateM1Progress(-10) - SEEKBAR_OFFSET);
				break;
				
			case R.id.incM2Btn:
				updateM2(updateM2Progress(1) - SEEKBAR_OFFSET);	
				break;
				
			case R.id.incXM2Btn:
				updateM2(updateM2Progress(10) - SEEKBAR_OFFSET);
				break;				
				
			case R.id.decM2Btn:
				updateM2(updateM2Progress(-1) - SEEKBAR_OFFSET);
				break;
				
			case R.id.decXM2Btn:
				updateM2(updateM2Progress(-10) - SEEKBAR_OFFSET);
				break;	
				
			case R.id.joinCheckBox://Set M2 seekbar the same a M1 seekbar	
				
				if(joinCheckbox.isChecked()){
					int tempProgVal = updateM1Progress(0);
					sbM2.setProgress(tempProgVal);
					updateM2(tempProgVal - SEEKBAR_OFFSET);	
				}
				break;
				
			case R.id.sendcmdbtn://Send user input command to motor controller				
				//usbCommThread.sendCommand("?fid\n\r");
				usbCommThread.sendCommand((sendCmdTxt.getText().toString().trim() + "\n\r"));	
				break;					
			
			default:
				break;
		}
	}

	/*
	 * increments M1 seek bar by input parameter and returns new progress value
	 */
	private int updateM1Progress(int i) {	
		sbM1.setProgress((sbM1.getProgress() + i));		
		return sbM1.getProgress();
	}
	
	/*
	 * Converts M2 speed value to correct progress bar position
	 * and updates progress bar and cmd text box
	 */
	private int updateM2Progress(int i) {		
		sbM2.setProgress((sbM2.getProgress() + i));		
		return sbM2.getProgress();
	}	

	/*
	 * Set M1 speed and update cmd text
	 */
	private void updateM1(int value) {
		//clip out of range values
		value = Math.min(Math.max(value, -SEEKBAR_OFFSET), SEEKBAR_OFFSET);		
		usbCommThread.sendCommand(roboCommand("g", 1, value));
    	if(joinCheckBox.isChecked()){	    			
    		//update other seek bar when in join mode
    		usbCommThread.sendCommand(roboCommand("g", 2, value));    		
    		sbM2.setProgress(sbM1.getProgress());
    		cmdM2Txt.setText(String.valueOf(sbM2.getProgress()-SEEKBAR_OFFSET));
    	}	
    	cmdM1Txt.setText(String.valueOf(sbM1.getProgress()-SEEKBAR_OFFSET));
	}

	/*
	 * Set M2 speed and update cmd text
	 */
	private void updateM2(int value) {
		//clip out of range values
		value = Math.min(Math.max(value, -SEEKBAR_OFFSET), SEEKBAR_OFFSET);
		usbCommThread.sendCommand(roboCommand("g", 2, value));
    	if(joinCheckBox.isChecked()){
    		//update other seek bar when in join mode	
    		usbCommThread.sendCommand(roboCommand("g", 1, value));	    		    	
    		sbM1.setProgress(sbM2.getProgress());
    		cmdM1Txt.setText(String.valueOf(sbM1.getProgress()-SEEKBAR_OFFSET));
    	}
    	cmdM2Txt.setText(String.valueOf(sbM2.getProgress()-SEEKBAR_OFFSET));
	}
	
	/*
	 * Enable UI controls after connection is made
	 */
	protected void enableUI(){	
		m1StopBtn.setEnabled(true);
		m2StopBtn.setEnabled(true);	
		incM1Btn.setEnabled(true);
		incXM1Btn.setEnabled(true);
		decM1Btn.setEnabled(true);
		decXM1Btn.setEnabled(true);
		incM2Btn.setEnabled(true);
		incXM2Btn.setEnabled(true);
		decM2Btn.setEnabled(true);
		decXM2Btn.setEnabled(true);
		sbM1.setEnabled(true);
		sbM2.setEnabled(true);
		sendCmdBtn.setEnabled(true);
		//connectUSB.setEnabled(false);
	
		
	}
	
	/*
	 * Forms command string message given motor channel and desired speed value
	 */
	protected String roboCommand(String commandCode, int motor, int speed){
		return "!" + commandCode + " " + String.valueOf(motor) + " " + String.valueOf(speed) + "\n\r";
	}
	/*
	 * Display list of available usb devices that the user can select
	 */
	private void showUSBDeviceDropDown() {
		alertDialog.show();
	}

	/*
	 * Add items to usb devices spinner and set click listener
	 */
	private void addItemsToUSBDeviceAdapter(List<String> list) {		

		dataAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, list);		
	
        alertDialog.setAdapter(dataAdapter, new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which) {									
				mUsbManager.requestPermission(deviceList.get(dataAdapter.getItem(which)), mPermissionIntent);			
			}        	
        });					
		
	}

	/*
	 * Get USB connection based on user device selection
	 */
	//protected void connectUSB(String deviceSelectionKey) {
	protected void connectUSB(UsbDevice device) {		
    	UsbInterface intf = null;
    	UsbEndpoint endpoint = null;
    	UsbEndpoint endpointOut = null;
    	UsbEndpoint endpointIn = null;
    	UsbDeviceConnection connection = null;    	
    	//Find output interface
    	for(int i = 0; i < device.getInterfaceCount(); i++){
    		intf = device.getInterface(i);
    		for(int j = 0; j < intf.getEndpointCount(); j++){
    			endpoint = intf.getEndpoint(j);
    			if(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK){
    			//if(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT){
    				if(endpoint.getDirection() == UsbConstants.USB_DIR_OUT){
    					endpointOut = endpoint;//output endpoint
    					//outputTxt.append("output:" + intf.getId()  + "\n");
    				}
    				else if(endpoint.getDirection() == UsbConstants.USB_DIR_IN) {    					
    					endpointIn = endpoint;//input endpoint
    					//outputTxt.append("input:" + intf.getId() + "\n");    					
    				}
    			}
    		}//endpoint loop
    		if(endpointOut != null && endpointIn != null){    		
    			break;
    		}
    	}//interface loop       
        connection = mUsbManager.openDevice(device);        
        if(connection != null){        
          	connection.claimInterface(intf, true); 
          	//start usb communication thread
          	usbCommThread.setmConnection(connection);
          	usbCommThread.setmEndpointOut(endpointOut);
          	usbCommThread.setmEndpointIn(endpointIn);
          	if(!usbCommThread.isAlive()){
          		usbCommThread.start();
          	}          	
          	outputTxt.setText("Connected to " + device.toString());
          	enableUI();
          }else{
       	   	outputTxt.setText("DATA NOT-SENT!!!!");
          }          
	}

	/*
	 * Returns hashmap of currently connected USB devices
	 */
	private HashMap<String, UsbDevice> getUSBDeviceList() {
    	HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
    	return deviceList;
	}

	@Override
	public void onProgressChanged(SeekBar sb, int progress, boolean isUser) {
		
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		// TODO Auto-generated method stub
		
	}

	/*
	 * (non-Javadoc)
	 * @see android.widget.SeekBar.OnSeekBarChangeListener#onStopTrackingTouch(android.widget.SeekBar)
	 * Seekbar stop trackingtouch listener
	 */
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
    	int id = seekBar.getId();
    	int value = seekBar.getProgress() - SEEKBAR_OFFSET;    	
    	switch(id){		
			case R.id.seekBarM1: 			
				updateM1(value);
			break;
			
			case R.id.seekBarM2:
				updateM2(value);
			break;
			
			default:
				break;
    	}//	
	}	

	/*
	 * Thread for handle read and write communication with USB device
	 */
	private class usbCommThread extends Thread{

		private UsbDeviceConnection mConnection = null;
		private UsbEndpoint mEndpointOut = null;
		private UsbEndpoint mEndpointIn = null;	
        private StringBuilder str = new StringBuilder();
        private int count = 0;

		/**
		 * 
		 */
		public usbCommThread() {
			super();
			// TODO Auto-generated constructor stub
		}
		
		/**
		 * @param connection
		 * @param endpointOut
		 * @param endpointIn
		 */
		public usbCommThread(UsbDeviceConnection connection,
				UsbEndpoint endpointOut, UsbEndpoint endpointIn) {
			this.mConnection = connection;
			this.mEndpointOut = endpointOut;
			this.mEndpointIn = endpointIn;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {			
			byte[] buffer = new byte[255];
            int count = 0;	      
			while(true){				
		        count =  mConnection.bulkTransfer(mEndpointIn, buffer, buffer.length, mEndpointIn.getInterval());	        	
		        if(count > 0){		        
		        	Message msg = Message.obtain(uiHandler, UIHandler.ID_0);
					msg.obj = (new String(buffer, Charset.forName("US-ASCII"))).trim();
					uiHandler.sendMessage(msg);	
					buffer = new byte[255];//clear out old data after each use
		        }
			}//end while loop
		}

		/*
		 * Send command to USB device
		 */
		public void sendCommand(String cmd){
	        synchronized (this) {
			byte[] bytes = cmd.getBytes();
	        if(mConnection != null){	          	 
	          	 mConnection.bulkTransfer(mEndpointOut, bytes, bytes.length, TIMEOUT); //do in another thread	          	 
	          }         	
	        }
		}


		/**
		 * @return the mConnection
		 */
		public UsbDeviceConnection getmConnection() {
			return mConnection;
		}

		/**
		 * @param mConnection the mConnection to set
		 */
		public void setmConnection(UsbDeviceConnection mConnection) {
			this.mConnection = mConnection;
		}

		/**
		 * @return the mEndpointOut
		 */
		public UsbEndpoint getmEndpointOut() {
			return mEndpointOut;
		}

		/**
		 * @param mEndpointOut the mEndpointOut to set
		 */
		public void setmEndpointOut(UsbEndpoint mEndpointOut) {
			this.mEndpointOut = mEndpointOut;
		}

		/**
		 * @return the mEndpointIn
		 */
		public UsbEndpoint getmEndpointIn() {
			return mEndpointIn;
		}

		/**
		 * @param mEndpointIn the mEndpointIn to set
		 */
		public void setmEndpointIn(UsbEndpoint mEndpointIn) {
			this.mEndpointIn = mEndpointIn;
		}

		/**
		 * @return the str
		 */
		public StringBuilder getStr() {
			return str;
		}

		/**
		 * @param str the str to set
		 */
		public void setStr(StringBuilder str) {
			this.str = str;
		}	
	}//end inner class def

	/**
	 * @author Maurice Tedder
	 * Based on code from: http://creating-industrial-solutions.com/author/sascha/
	 *
	 */
	class UIHandler extends Handler {
	    private static final int ID_0 = 0;
	    private static final int ID_1 = 1;
	 
	    @Override
	    public void handleMessage(Message msg) {	    	
	    	switch (msg.what) {
		        case ID_0:
		            // a message is received; update UI text view
		            if (msg.obj != null){
		            	dataValues.clear();		            			            	
		            	dataValues.add((String)msg.obj);
		            	adapter.notifyDataSetChanged();		            	
		            }	            							           
		            break;
		 
		        default:
		            break;
	        }
	        super.handleMessage(msg);
	    }
	}
}
