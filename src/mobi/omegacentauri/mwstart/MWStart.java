package mobi.omegacentauri.mwstart;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MWStart extends Activity {
	private static final String PREF_LAST_DEVICE = "lastDevice";
	private BluetoothAdapter btAdapter;
	private TextView message;
	private Button goButton;
	private ListView deviceList;
	private SharedPreferences options;
	private ArrayAdapter<BluetoothDevice> deviceSelectionAdapter;
	private boolean brainLinkMode = false;
	private static final byte[] UPSCALED02 = new byte[] {0x00, 0x7E, 0x00, 0x00, 0x00, (byte)0xF8};

	private void message(final String s) {
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				message.setText(s);
			}
		});
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		options = PreferenceManager.getDefaultSharedPreferences(this);

		Log.v("MWStart", "OnCreate");
		
		setContentView(R.layout.main);
		
		message = (TextView)findViewById(R.id.message);
		goButton = (Button)findViewById(R.id.go);
		deviceList = (ListView)findViewById(R.id.devices);
	}
	
	@Override
	public void onPause() {
		super.onPause();
	}
	
	public void clickedOn(View v) {
		for (int i = 0 ; i < deviceList.getCount(); i++) {
			if (deviceList.isItemChecked(i)) {
				new InitializeTask(this).execute(deviceSelectionAdapter.getItem(i));
				return;
			}
		}
		
		Toast.makeText(this, "Select a device", Toast.LENGTH_LONG).show();
	}
	
	protected boolean testTG(byte[] data) {
//		for (int i = 0 ; i < data.length ; i += 16) {
//			String out = "";
//			for (int j = 0 ; i + j < data.length ; j++)
//				out += String.format("%02x ", data[i+j]);
//			Log.v("MWStart", out);
//		}
		for (int i = 0 ; i < data.length - 8 ; i++) {
			int len;
			if (data[i] == (byte)0xAA && data[i+1] == (byte)0xAA && (len = 0xFF&(int)data[i+2]) == 4 && 
					data[i+3] == (byte)0x80
					) {
				Log.v("MWStart", "found AA AA 04 80 at "+i);
				if (i + len + 3 >= data.length) 
					continue;
				byte sum = 0;
				for (int j = i + 3; j < i + 3 + len ; j++) {
					sum += data[j];
				}
				Log.v("MWStart", "sum "+sum+" vs "+data[i+3+len]);
				
				return (0xFF&(sum ^ 0xFF)) == (0xFF&data[i + 3 + len]);
			}
		}
		return false;
	}

	protected void readWithTimeout(InputStream is, byte[] data, int timeout) throws IOException {
		int pos = 0;
		long t1 = System.currentTimeMillis() + timeout;
		
		while (pos < data.length && System.currentTimeMillis() <= t1) {
			int n = is.available();
			
			if (n > 0) {
				if (pos + n > data.length)
					n = data.length - pos;
				Log.v("MWStart", "reading "+n+" bytes");
				is.read(data, pos, n);
				pos += n;
			}
		}
		
		if (pos < data.length)
			new IOException("Cannot read "+data.length+" bytes.");
	}

	@Override
	public void onResume() {
		super.onResume();
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		final List<BluetoothDevice> devs = new ArrayList<BluetoothDevice>();
		devs.addAll(btAdapter.getBondedDevices());
		Collections.sort(devs, new Comparator<BluetoothDevice>(){

			@Override
			public int compare(BluetoothDevice lhs, BluetoothDevice rhs) {
				return String.CASE_INSENSITIVE_ORDER.compare(lhs.getName(), rhs.getName());
			}});
		
		deviceSelectionAdapter = new ArrayAdapter<BluetoothDevice>(this, 
				R.id.devices, devs) {
			public View getView(int position, View convertView, ViewGroup parent) {
				View v;				

				if (convertView == null) {
					v = View.inflate(MWStart.this, android.R.layout.two_line_list_item, null);
				}
				else {
					v = convertView;
				}
				
				if (deviceList.isItemChecked(position)) {
					v.setBackgroundColor(Color.BLUE);
				}
				else {
					v.setBackgroundColor(Color.BLACK);
				}
				
				BluetoothDevice dev = getItem(position);
				TextView line1 = (TextView)v.findViewById(android.R.id.text1);
				line1.setText(dev.getName());
				TextView line2 = (TextView)v.findViewById(android.R.id.text2);
				line2.setText(dev.getAddress());
				
				return v;
			}


		};
		deviceList.setAdapter(deviceSelectionAdapter);
		String lastDev = options.getString(PREF_LAST_DEVICE, "(none)");
		for (int i = 0 ; i < devs.size() ; i++) {
			deviceList.setItemChecked(i, devs.get(i).getAddress().equals(lastDev));
		} 
		Log.v("MWStart", "selection "+deviceList.getSelectedItemPosition());
		
		deviceList.setOnItemClickListener(new ListView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long arg3) {
				options.edit().putString(PREF_LAST_DEVICE, deviceSelectionAdapter.getItem(position).getAddress()).commit();				
				deviceList.setItemChecked(position, true);
			}
		});
		deviceList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	}
	
	class InitializeTask extends AsyncTask<BluetoothDevice, String, String>{
		private ProgressDialog progressDialog;

		public InitializeTask(Context c) {
			progressDialog = new ProgressDialog(c);
			progressDialog.setCancelable(false);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			progressDialog.show();
		}
		
		@Override
		public void onProgressUpdate(String... msg) {
			progressDialog.setMessage(msg[0]);
		}
	
		@Override
		protected String doInBackground(BluetoothDevice... device) {
			
			String error = "";
			
			boolean needPowercycle = false;
			
			boolean done = false;
			
			for (int i = 0 ; i < 3 && !done && !needPowercycle ; i++) {
				BluetoothSocket sock = null;
				OutputStream os = null;
				InputStream is = null;

				try {
					publishProgress("Connecting");
					needPowercycle = false;
					Log.v("MWStart", "getting output stream");
					if (Build.VERSION.SDK_INT >= 10)
						sock = device[0].createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
					else {
						Method m;
						try {
							m = device.getClass().getMethod("createInsecureRfcommSocket", new Class[] {int.class});
							sock = (BluetoothSocket) m.invoke(device[0], 1);
						}
						catch (Exception e) {
							sock = device[0].createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
						}
					}
					sock.connect();
					sleep(50);
					os = sock.getOutputStream();
					publishProgress("Setting up link");
					
					if (brainLinkMode) {
						needPowercycle = true;
						os.write(new byte[] { '*', 'u', '9', '6', 't', 1, 0x02, 'u', '5', '7', 'Z' });
					}
					else {
						os.write(new byte[] { '*', 'u', '5', '7', 'Z' });
						sleep(200);
						os.write(UPSCALED02);
					}
					
					done = needPowercycle;
					publishProgress("Verifying connection");
					is = sock.getInputStream();
					clearBuffer(is);
					byte[] data512 = new byte[512]; 
					readWithTimeout(is, data512, 2000);
					boolean test = testTG(data512);
					for (int j = 0 ; ! test && j < 2 ; j++ ) {
						publishProgress("Error verifying, trying again");
						Log.v("MWStart", "retrying");
						os.write(UPSCALED02);
						clearBuffer(is);
						readWithTimeout(is, data512, 2000);
						test = testTG(data512);
					}
					error = test ? "Successful initiation!" : "Cannot read valid data.";
					done = true;
				} catch (IOException e) {
					Log.v("MWStart", "Error "+e+".");
					error = "Error: "+e+".";
					if (i + 1 < 3 && !done && !needPowercycle) {
						publishProgress("Error, will try again");
						sleep(1000);
					}
				} finally {
					if (os != null)
						try {
							os.close();
						} catch (IOException e) {
						}
					if (is != null)
						try {
							is.close();
						} catch (IOException e) {
						}
					if (sock != null)
						try {
							sock.close();
						} catch (IOException e) {
						}
				}
			}

			return error + (needPowercycle ? " Need to turn off link and headset before trying again." : "");
		}
		
		@Override
		protected void onPostExecute(String message) {
			MWStart.this.message.setText(message);
			progressDialog.dismiss();
		}
		
	}

	public static void clearBuffer(InputStream is) {
		int avail;
		try {
			avail = is.available();
			is.skip(avail);		
		} catch (IOException e) {
		}
	}

	public static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e2) {
		}
	}
}
