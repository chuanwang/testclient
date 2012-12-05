package com.hcdev.droidmoteclient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.view.MotionEvent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;

public class ClientActivity extends Activity implements SensorEventListener {

	private static final String TAG = "DroidmoteClient";

	private HandlerThread mCommandThread;
	private Handler mCommandHandler;
	private static final int MOUSE_EVENT = 1;
	private static final int KEY_EVENT = 2;
	private static final int MOTION_EVENT = 3;
	private static final int CONNECT_SERVER_EVENT = 10;
    // private static final int DISCONNECT_SERVER_EVENT = 11;
	private static final int FINISH_EVENT = 99;

	public static final int DIALOG_SETTINGS = 1;
	
	/** The DATA_IP constant for SharedPreferences. */
	public static final String TCP_DATA_IP = "serverIP";

	/** The DATA_PORT constant for SharedPreferences. */
	public static final String TCP_DATA_PORT = "port";

	/** The Constant DEFAULT_IP. */
	public static final String TCP_DEFAULT_IP = "192.168.0.1";

	/** The Constant DEFAULT_PORT. */
	public static final int TCP_DEFAULT_PORT = 44522;

	private Button mUpBtn;
	private Button mDownBtn;
    private Button mLeftBtn;
    private Button mRightBtn;
	private Button mConnectBtn;
    private Button mDisconnectBtn;
	private EditText mTextIP;
	private EditText mTextPort;
	private Dialog mSettingsDialog;

	private Handler mUIHandler = new Handler();

	private SendCommandTCP mSendCommandTCP = null;

	private class CommandHandler extends Handler {
		public CommandHandler(Looper looper) {
			super(looper);
		}
		
		private void handleCommand(Intent intent) {
			String type = intent.getStringExtra("type");
			String value = intent.getStringExtra("value");
			String action = intent.getStringExtra("action");
			String ts = intent.getStringExtra("timestamp");
			
			// Log.d(TAG, "type: " + type + ", value: " + value);
			String command = type + ":" + value + ":" + action + ":" + ts;
			Log.d(TAG, "==> : Execute command: " + command);
			if(mSendCommandTCP != null){
				mSendCommandTCP.execute(command);
			}
			Log.d(TAG, "<== Execute command: " + command);
		}
	
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case KEY_EVENT:
				handleCommand((Intent)msg.obj);
				break;
				
			case CONNECT_SERVER_EVENT:
				if (connectServer()) {
				    mUIHandler.post(new ServerConnectRunnable());
				}
				break;
			case MOTION_EVENT:
				handleCommand((Intent)msg.obj);
				break;
			case FINISH_EVENT:
				Log.d(TAG, "end of command thread");
				this.getLooper().quit();
				break;
			}
		}
	}

	/**
	 * The TCP Sender
	 */
	class SendCommandTCP {
	
		/** The socket. */
		Socket s = null;
	
		/** The output Stream. */
		OutputStream out = null;
	
		/**
		 * Instantiates a new send command tcp.
		 * 
		 * @param IP the IP
		 * @param port the port
		 */
		public SendCommandTCP(Socket socket) {
			this.s = socket;
            try {
                out = s.getOutputStream();
            } catch (IOException e) {
                Log.w(TAG, "error get outputstream of socket: " + e);
            }
		}

		public void close() {
		    if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
		    }
			try {
				s.close();
			} catch (IOException e) {
				// Log.w(TAG, "Error close socket");
			}
		}

		private void execute(String msg) {
	
		    if (out == null) return;

			try {
				// s = new Socket(IP, port);
	
				msg += "\n";
				Log.d(TAG,"sending msg to socket out");
				out.write(msg.getBytes());
				// measureTime("----------------written");
	
			} catch (IOException e) {
				Log.w(TAG, "TCP error when send command to server socket: " + e.getMessage());
				// close();
				mUIHandler.post(new ServerDisconnectRunnable());
			} finally {
//				try {
//					if (out != null) out.close();
//				} catch (IOException e) {
//				}
//				try {
//					s.close();
//				} catch (Exception e) {
//				}
			}
	
		}
	}

	private class ServerDisconnectRunnable implements Runnable {

        //@Override
        public void run() {
            disconnectServer();
        }
	    
	}

	private class ServerConnectRunnable implements Runnable {

        //@Override
        public void run() {
            updateConnectionState(true);
        }
	    
	}
	
	private void updateConnectionState(boolean connected) {
	    mConnectBtn.setEnabled(!connected);
	    mDisconnectBtn.setEnabled(connected);
	}
	public boolean onTouchEvent(MotionEvent ev)
	{
		Intent i = new Intent();
		i.putExtra("type","touch");
		i.putExtra("value",Float.toString(ev.getRawX())+":"+Float.toString(ev.getRawY()));
		i.putExtra("action",Integer.toString(ev.getAction()));
		i.putExtra("timestamp", Long.toString(ev.getEventTime()));
		Message.obtain(mCommandHandler, MOTION_EVENT, i).sendToTarget();
		return true;
	}
	
	public void onSensorChanged(SensorEvent ev) {
		
	}
	public void onSensorAccuracyChanged(Sensor sensor, int accuracy) {
		
	}
	
	private View.OnClickListener mClickButton = new View.OnClickListener() {
		
		//@Override
		public void onClick(View v) {
			if (v == mConnectBtn) {
				Message.obtain(mCommandHandler, CONNECT_SERVER_EVENT).sendToTarget();
			} else if (v == mDisconnectBtn) {
			    disconnectServer();
			} else {
				Intent i = new Intent();
				i.putExtra("type", "key");
				
				if (v == mUpBtn) {
					i.putExtra("value", Integer.toString(KeyEvent.KEYCODE_DPAD_UP)+":0");
				} else if (v == mDownBtn) {
					i.putExtra("value", Integer.toString(KeyEvent.KEYCODE_DPAD_DOWN)+":0");
				} else if (v == mLeftBtn) {
                    i.putExtra("value", Integer.toString(KeyEvent.KEYCODE_DPAD_LEFT)+":0");
				} else if (v == mRightBtn) {
                    i.putExtra("value", Integer.toString(KeyEvent.KEYCODE_DPAD_RIGHT)+":0");
				} 
				i.putExtra("action", "0");
				i.putExtra("timestamp", "0");
				Message.obtain(mCommandHandler, KEY_EVENT, i).sendToTarget();
				
			}
		}
	};

	private boolean connectServer() {
		int port = PreferenceManager.getDefaultSharedPreferences(ClientActivity.this).getInt(TCP_DATA_PORT, TCP_DEFAULT_PORT);
		String IP = PreferenceManager.getDefaultSharedPreferences(ClientActivity.this).getString(TCP_DATA_IP, TCP_DEFAULT_IP);
		boolean r = false;

		Log.d(TAG, "try to connect to server, IP: " + IP + ", port:" + port);
		try {
			Socket s = new Socket();
			InetSocketAddress addr = new InetSocketAddress(IP, port);
			s.connect(addr, 2000);
			s.setReuseAddress(true);
			s.setTcpNoDelay(true);
			s.setSoTimeout(1);

			mSendCommandTCP = new SendCommandTCP(s);
			r = true;
			Log.d(TAG, "Connect to server successfully, IP: " + IP + ", port:" + port);
		} catch (SocketException e) {
			Log.w(TAG, "Fail connect to server, error: " + e);
		} catch (IOException e) {
			Log.w(TAG, "Fail connect to server, error: " + e);
		}
		
		return r;
	}

	private void disconnectServer() {
		if(mSendCommandTCP != null){
			mSendCommandTCP.close();
			mSendCommandTCP = null;
		}
        updateConnectionState(false);
	}
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mUpBtn = (Button)findViewById(R.id.up);
        mDownBtn = (Button)findViewById(R.id.down);
        mLeftBtn = (Button)findViewById(R.id.left);
        mRightBtn = (Button)findViewById(R.id.right);
        mConnectBtn = (Button)findViewById(R.id.connect);
        mDisconnectBtn = (Button)findViewById(R.id.disconnect);
        mUpBtn.setOnClickListener(mClickButton);
        mDownBtn.setOnClickListener(mClickButton);
        mLeftBtn.setOnClickListener(mClickButton);
        mRightBtn.setOnClickListener(mClickButton);
        mConnectBtn.setOnClickListener(mClickButton);
        mDisconnectBtn.setOnClickListener(mClickButton);

		mCommandThread= new HandlerThread("CommandHandler", android.os.Process.THREAD_PRIORITY_BACKGROUND);
		mCommandThread.start();
		mCommandHandler = new CommandHandler(mCommandThread.getLooper());

		SensorManager manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		Sensor accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if(!manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)){
			Log.d(TAG,"Error, could not register sensor listener");
		}
    }

    @Override
    public void onDestroy() {
    	if (mSendCommandTCP != null) mSendCommandTCP.close();
    	super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.d(TAG, "onConfigurationChanged, config=" + config);
        super.onConfigurationChanged(config);
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.setIP:
			showDialog(DIALOG_SETTINGS);
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_SETTINGS:
			return createSettingsDialog();
		default:
			return super.onCreateDialog(id);
		}

	}
	
	private Dialog createSettingsDialog() {
		
		mSettingsDialog = new Dialog(this);
		mSettingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		mSettingsDialog.setContentView(R.layout.settings_popup);
	
		mTextIP = (EditText) mSettingsDialog.findViewById(R.id.edIp);
		mTextIP.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(TCP_DATA_IP, TCP_DEFAULT_IP));
	
		mTextPort = (EditText) mSettingsDialog.findViewById(R.id.edPort);
		mTextPort.setText(PreferenceManager.getDefaultSharedPreferences(this).getInt(TCP_DATA_PORT, TCP_DEFAULT_PORT) + "");
	
		Button save = (Button) mSettingsDialog.findViewById(R.id.ok);
		save.setOnClickListener(new OnClickListener() {
	
			//@Override
			public void onClick(View v) {
				int portInt = TCP_DEFAULT_PORT;
				try {
					portInt = Integer.parseInt(mTextPort.getText().toString());
				} catch (Exception e) {
					Toast.makeText(ClientActivity.this, R.string.invalidPort, Toast.LENGTH_SHORT).show();
					return;
				}
				if (portInt > 65535) {
					Toast.makeText(ClientActivity.this, R.string.invalidPort, Toast.LENGTH_SHORT).show();
					return;
				}
				Editor editor = PreferenceManager.getDefaultSharedPreferences(ClientActivity.this).edit();
				editor.putString(TCP_DATA_IP, mTextIP.getText().toString());
				editor.putInt(TCP_DATA_PORT, portInt);
				editor.commit();

				mSettingsDialog.dismiss();
				// this.removeDialog(SuperActivity.DIALOG_SETTINGS);
	
			}
		});
	
		Button cancel = (Button) mSettingsDialog.findViewById(R.id.cancel);
		cancel.setOnClickListener(new OnClickListener() {
	
			//@Override
			public void onClick(View v) {
				mSettingsDialog.dismiss();
				//  this.removeDialog(SuperActivity.DIALOG_SETTINGS);
			}
		});
	
		mSettingsDialog.setOnCancelListener(new OnCancelListener() {
	
			//@Override
			public void onCancel(DialogInterface dialog) {
				// this.removeDialog(DIALOG_SETTINGS);
				mSettingsDialog.dismiss();
			}
		});
	
		return mSettingsDialog;
	}
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}
}