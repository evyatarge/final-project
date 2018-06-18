package es.pymasde.blueterm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;


public class BlueTerm extends Activity {
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    
	public static final int ORIENTATION_SENSOR    = 0;
	public static final int ORIENTATION_PORTRAIT  = 1;
	public static final int ORIENTATION_LANDSCAPE = 2;

    //holding the bluetooth sensors number and list
    protected static int numOfSensors = 3;
    protected static List<TextView> sensors = new ArrayList<TextView>();

    private static TextView mTitle;

    // Name of the connected device
    private String mConnectedDeviceName = null;

    /**
     * Set to true to add debugging code and logging.
     */
    public static final boolean DEBUG = true;

    /**
     * Set to true to log each character received from the remote process to the
     * android log, which makes it easier to debug some kinds of problems with
     * emulating escape sequences and control codes.
     */
    public static final boolean LOG_CHARACTERS_FLAG = DEBUG && false;

    /**
     * Set to true to log unknown escape sequences.
     */
    public static final boolean LOG_UNKNOWN_ESCAPE_SEQUENCES = DEBUG && false;

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
	public static final String LOG_TAG = "BlueTerm";

    // Message types sent from the BluetoothReadService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;	

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
	private BluetoothAdapter mBluetoothAdapter = null;
	
    /**
     * Our main view. Displays the emulated terminal screen.
     */
    private EmulatorView mEmulatorView;

    /**
     * A key listener that tracks the modifier keys and allows the full ASCII
     * character set to be entered.
     */
    private TermKeyListener mKeyListener;
		
	
    private static BluetoothSerialService mSerialService = null;
    
	private static InputMethodManager mInputManager;
	
	private boolean mEnablingBT;
    private boolean mLocalEcho = false;
    private int mFontSize = 9;
    private int mColorId = 2;
    private int mControlKeyId = 0;
    private boolean mAllowInsecureConnections = true;
    private int mIncomingEoL_0D = 0x0D;
    private int mIncomingEoL_0A = 0x0A;
    private int mOutgoingEoL_0D = 0x0D;
    private int mOutgoingEoL_0A = 0x0A;
    
    private int mScreenOrientation = 0;

    private static final String LOCALECHO_KEY = "localecho";
    private static final String FONTSIZE_KEY = "fontsize";
    private static final String COLOR_KEY = "color";
    private static final String CONTROLKEY_KEY = "controlkey";
    private static final String ALLOW_INSECURE_CONNECTIONS_KEY = "allowinsecureconnections";
    private static final String INCOMING_EOL_0D_KEY = "incoming_eol_0D";
    private static final String INCOMING_EOL_0A_KEY = "incoming_eol_0A";
    private static final String OUTGOING_EOL_0D_KEY = "outgoing_eol_0D";
    private static final String OUTGOING_EOL_0A_KEY = "outgoing_eol_0A";
    private static final String SCREENORIENTATION_KEY = "screenorientation";

    public static final int WHITE = 0xffffffff;
    public static final int BLACK = 0xff000000;
    public static final int BLUE = 0xff344ebd;

    private static final int[][] COLOR_SCHEMES = {
        {BLACK, WHITE}, {WHITE, BLACK}, {WHITE, BLUE}};

    private static final int[] CONTROL_KEY_SCHEMES = {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_AT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_UP
    };
//    private static final String[] CONTROL_KEY_NAME = {
//        "Ball", "@", "Left-Alt", "Right-Alt"
//    };
    private static String[] CONTROL_KEY_NAME;

    private int mControlKeyCode;

    private SharedPreferences mPrefs;
	
    private MenuItem mMenuItemConnect;
    private MenuItem mMenuItemStartStopRecording;
    
    private Dialog         mAboutDialog;

    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		if (DEBUG)
			Log.e(LOG_TAG, "+++ ON CREATE +++");

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        readPrefs();

        CONTROL_KEY_NAME = getResources().getStringArray(R.array.entries_controlkey_preference);

    	mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);		
		
        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);


        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBluetoothAdapter == null) {
            finishDialogNoBluetooth();
			return;
		}
		
        setContentView(R.layout.term_activity);

        mEmulatorView = (EmulatorView) findViewById(R.id.emulatorView);


//        int[] ids = {R.id.sensData_1, R.id.sensData_2, R.id.sensData_3};
//
//        for(int i = 0; i<ids.length; i++) {
//            View v = findViewById(ids[i]);
//        }

//        for(int i=0; i<numOfSensors; i++) {
//            sens = (TextView) findViewById(R.id.sensData_1);
//        }
//        sensorB = (TextView) findViewById(R.id.sensData_2);
//        sensorC = (TextView) findViewById(R.id.sensData_3);


        for (int i = 1; i <= numOfSensors; i++) {
            String name = "sensData_"+i;
            int id = getResources().getIdentifier(name, "id", getPackageName());
            if (id != 0) {
                sensors.add((TextView) findViewById(id));
            }
        }

        mEmulatorView.initialize( this );

        mKeyListener = new TermKeyListener();

        mEmulatorView.setFocusable(true);
        mEmulatorView.setFocusableInTouchMode(true);
        mEmulatorView.requestFocus();
        mEmulatorView.register(mKeyListener);

        mSerialService = new BluetoothSerialService(this, mHandlerBT, mEmulatorView);


		if (DEBUG)
			Log.e(LOG_TAG, "+++ DONE IN ON CREATE +++");
	}

	@Override
	public void onStart() {
		super.onStart();
		if (DEBUG)
			Log.e(LOG_TAG, "++ ON START ++");
		
		mEnablingBT = false;
	}

	@Override
	public synchronized void onResume() {
		super.onResume();

		if (DEBUG) {
			Log.e(LOG_TAG, "+ ON RESUME +");
		}
		
		if (!mEnablingBT) { // If we are turning on the BT we cannot check if it's enable
		    if ( (mBluetoothAdapter != null)  && (!mBluetoothAdapter.isEnabled()) ) {
			
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.alert_dialog_turn_on_bt)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.alert_dialog_warning_title)
                    .setCancelable( false )
                    .setPositiveButton(R.string.alert_dialog_yes, new DialogInterface.OnClickListener() {
                    	public void onClick(DialogInterface dialog, int id) {
                    		mEnablingBT = true;
                    		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    		startActivityForResult(enableIntent, REQUEST_ENABLE_BT);			
                    	}
                    })
                    .setNegativeButton(R.string.alert_dialog_no, new DialogInterface.OnClickListener() {
                    	public void onClick(DialogInterface dialog, int id) {
                    		finishDialogNoBluetooth();            	
                    	}
                    });
                AlertDialog alert = builder.create();
                alert.show();
		    }		
		
		    if (mSerialService != null) {
		    	// Only if the state is STATE_NONE, do we know that we haven't started already
		    	if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
		    		// Start the Bluetooth chat services
		    		mSerialService.start();
		    	}
		    }

		    if (mBluetoothAdapter != null) {
		    	readPrefs();
		    	updatePrefs();

		    	mEmulatorView.onResume();
		    }
		}
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mEmulatorView.updateSize();
    }

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (DEBUG)
			Log.e(LOG_TAG, "- ON PAUSE -");

		if (mEmulatorView != null) {
			mInputManager.hideSoftInputFromWindow(mEmulatorView.getWindowToken(), 0);
			mEmulatorView.onPause();
		}
	}

    @Override
    public void onStop() {
        super.onStop();
        if(DEBUG)
        	Log.e(LOG_TAG, "-- ON STOP --");
    }


	@Override
	public void onDestroy() {
		super.onDestroy();
		if (DEBUG)
			Log.e(LOG_TAG, "--- ON DESTROY ---");
		
        if (mSerialService != null)
        	mSerialService.stop();
        
	}

    private void readPrefs() {
        mLocalEcho = mPrefs.getBoolean(LOCALECHO_KEY, mLocalEcho);
        mFontSize = readIntPref(FONTSIZE_KEY, mFontSize, 20);
        mColorId = readIntPref(COLOR_KEY, mColorId, COLOR_SCHEMES.length - 1);
        mControlKeyId = readIntPref(CONTROLKEY_KEY, mControlKeyId, CONTROL_KEY_SCHEMES.length - 1);
        mAllowInsecureConnections = mPrefs.getBoolean( ALLOW_INSECURE_CONNECTIONS_KEY, mAllowInsecureConnections);

        mIncomingEoL_0D = readIntPref(INCOMING_EOL_0D_KEY, mIncomingEoL_0D, 0x0D0A);
        mIncomingEoL_0A = readIntPref(INCOMING_EOL_0A_KEY, mIncomingEoL_0A, 0x0D0A);
        mOutgoingEoL_0D = readIntPref(OUTGOING_EOL_0D_KEY, mOutgoingEoL_0D, 0x0D0A);
        mOutgoingEoL_0A = readIntPref(OUTGOING_EOL_0A_KEY, mOutgoingEoL_0A, 0x0D0A);
        
		mScreenOrientation = readIntPref(SCREENORIENTATION_KEY, mScreenOrientation, 2);
    }

    private void updatePrefs() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mEmulatorView.setTextSize((int) (mFontSize * metrics.density));
        setColors();
        mControlKeyCode = CONTROL_KEY_SCHEMES[mControlKeyId];
        mSerialService.setAllowInsecureConnections( mAllowInsecureConnections );
        
        if (mEmulatorView != null) {
            mEmulatorView.setIncomingEoL_0D( mIncomingEoL_0D );
            mEmulatorView.setIncomingEoL_0A( mIncomingEoL_0A );
        }
        
		switch (mScreenOrientation) {
		case ORIENTATION_PORTRAIT:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			break;
		case ORIENTATION_LANDSCAPE:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			break;
		default:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}
    }

    private int readIntPref(String key, int defaultValue, int maxValue) {
        int val;
        try {
            val = Integer.parseInt( mPrefs.getString(key, Integer.toString(defaultValue)) );
        } catch (NumberFormatException e) {
            val = defaultValue;
        }
        val = Math.max(0, Math.min(val, maxValue));
        return val;
    }
    
	public int getConnectionState() {
		return mSerialService.getState();
	}

	private byte[] handleEndOfLineChars( int outgoingEoL ) {
		byte[] out;
		
	    if ( outgoingEoL == 0x0D0A ) {
	    	out = new byte[2];
	    	out[0] = 0x0D;
	    	out[1] = 0x0A;
		}
	    else {
		    if ( outgoingEoL == 0x00 ) {
		    	out = new byte[0];
		    }
		    else {
		    	out = new byte[1];
		    	out[0] = (byte)outgoingEoL;
		    }
	    }
		
		return out;
	}

    public void send(byte[] out) {
    	
    	if ( out.length == 1 ) {
    		
    		if ( out[0] == 0x0D ) {
    			out = handleEndOfLineChars( mOutgoingEoL_0D );
    		}
    		else {
        		if ( out[0] == 0x0A ) {
        			out = handleEndOfLineChars( mOutgoingEoL_0A );
        		}
    		}
    	}
    	
    	if ( out.length > 0 ) {
    		mSerialService.write( out );
    	}
    }
    
    public void toggleKeyboard() {
  		mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }
    
    public int getTitleHeight() {
    	return mTitle.getHeight();
    }
    
    // The Handler that gets information back from the BluetoothService
    private final Handler mHandlerBT = new Handler() {
    	
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(DEBUG) Log.i(LOG_TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothSerialService.STATE_CONNECTED:
                	if (mMenuItemConnect != null) {
                		mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
                		mMenuItemConnect.setTitle(R.string.disconnect);
                	}
                	
//                	mInputManager.showSoftInput(mEmulatorView, InputMethodManager.SHOW_IMPLICIT);
                	
                    mTitle.setText( R.string.title_connected_to );
                    mTitle.append(" " + mConnectedDeviceName);
                    break;
                    
                case BluetoothSerialService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                    
                case BluetoothSerialService.STATE_LISTEN:
                case BluetoothSerialService.STATE_NONE:
                	if (mMenuItemConnect != null) {
                		mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
                		mMenuItemConnect.setTitle(R.string.connect);
                	}

            		mInputManager.hideSoftInputFromWindow(mEmulatorView.getWindowToken(), 0);
                	
                    mTitle.setText(R.string.title_not_connected);

                    break;
                }
                break;
            case MESSAGE_WRITE:
            	if (mLocalEcho) {
            		byte[] writeBuf = (byte[]) msg.obj;
            		mEmulatorView.write(writeBuf, msg.arg1);
            	}
                
                break;
/*                
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;              
                mEmulatorView.write(readBuf, msg.arg1);
                
                break;
*/                
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), getString(R.string.toast_connected_to) + " "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };    

    
    public void finishDialogNoBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dialog_no_bt)
        .setIcon(android.R.drawable.ic_dialog_info)
        .setTitle(R.string.app_name)
        .setCancelable( false )
        .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       finish();            	
                   }
               });
        AlertDialog alert = builder.create();
        alert.show(); 
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(DEBUG) Log.d(LOG_TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        
        case REQUEST_CONNECT_DEVICE:

            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mSerialService.connect(device);                
            }
            break;

        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode != Activity.RESULT_OK) {
                Log.d(LOG_TAG, "BT not enabled");
                
                finishDialogNoBluetooth();                
            }
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleControlKey(keyCode, true)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            return super.onKeyDown(keyCode, event);
        } else if (handleDPad(keyCode, true)) {
            return true;
        }

        // Translate the keyCode into an ASCII character.
        int letter = mKeyListener.keyDown(keyCode, event);

        if (letter >= 0) {
        	byte[] buffer = new byte[1];
        	buffer[0] = (byte)letter;
        	
        	send( buffer );
        	//mSerialService.write(buffer);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (handleControlKey(keyCode, false)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            return super.onKeyUp(keyCode, event);
        } else if (handleDPad(keyCode, false)) {
            return true;
        }

        mKeyListener.keyUp(keyCode);
        return true;
    }

    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mControlKeyCode) {
            mKeyListener.handleControlKey(down);
            return true;
        }
        return false;
    }

    /**
     * Handle dpad left-right-up-down events. Don't handle
     * dpad-center, that's our control key.
     * @param keyCode
     * @param down
     */
    private boolean handleDPad(int keyCode, boolean down) {
    	byte[] buffer = new byte[1];

        if (keyCode < KeyEvent.KEYCODE_DPAD_UP ||
                keyCode > KeyEvent.KEYCODE_DPAD_CENTER) {
            return false;
        }

        if (down) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            	buffer[0] = '\r';
            	//mSerialService.write( buffer );
            	send( buffer );
            } else {
                char code;
                switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    code = 'A';
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    code = 'B';
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    code = 'D';
                    break;
                default:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    code = 'C';
                    break;
                }
            	buffer[0] = 27; // ESC
            	//mSerialService.write( buffer );                    
            	send( buffer );
                if (mEmulatorView.getKeypadApplicationMode()) {
                	buffer[0] = 'O';
                	//mSerialService.write( buffer );
                	send( buffer );
                } else {
                	buffer[0] = '[';
                	//mSerialService.write( buffer );
                	send( buffer );
                }
            	buffer[0] = (byte)code;
            	//mSerialService.write( buffer );
            	send( buffer );
            }
        }
        return true;
    }

    private boolean isSystemKey(int keyCode, KeyEvent event) {
        return event.isSystem();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        mMenuItemConnect = menu.getItem(0);
        mMenuItemStartStopRecording = menu.getItem(3);        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.connect:
        	
        	if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
        		// Launch the DeviceListActivity to see devices and do scan
        		Intent serverIntent = new Intent(this, DeviceListActivity.class);
        		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        	}
        	else
            	if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
            		mSerialService.stop();
		    		mSerialService.start();
            	}
            return true;
        case R.id.preferences:
        	doPreferences();
            return true;
        case R.id.menu_special_keys:
            doDocumentKeys();
            return true;
        case R.id.menu_start_stop_save:
        	if (mMenuItemStartStopRecording.getTitle() == getString(R.string.menu_stop_logging) ) {
        		doStopRecording();
        	}	
        	else {
        		doStartRecording();
        	}
            return true;
            
        case R.id.menu_about:
        	showAboutDialog();
            return true;
        }
        return false;
    }
    
    private void doPreferences() {
        startActivity(new Intent(this, TermPreferences.class));
    }
    
    public void doOpenOptionsMenu() {
        openOptionsMenu();
    }

    private void setColors() {
        int[] scheme = COLOR_SCHEMES[mColorId];
        mEmulatorView.setColors(scheme[0], scheme[1]);
    }

    private void doDocumentKeys() {
        String controlKey = CONTROL_KEY_NAME[mControlKeyId];
        new AlertDialog.Builder(this).
            setTitle( getString(R.string.title_document_key_press) + " \"" + controlKey + "\" "+ getString(R.string.title_document_key_rest)).
            setMessage(" Space ==> Control-@ (NUL)\n"
                    + " A..Z ==> Control-A..Z\n"
                    + " I ==> Control-I (TAB)\n"
                    + " 1 ==> Control-[ (ESC)\n"
                    + " 5 ==> Control-_\n"
                    + " . ==> Control-\\\n"
                    + " 0 ==> Control-]\n"
                    + " 6 ==> Control-^").
            show();
     }


    private void doStartRecording() {
    	File sdCard = Environment.getExternalStorageDirectory();
    	
    	SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmss");
    	String currentDateTimeString = format.format(new Date());
    	String fileName = sdCard.getAbsolutePath() + "/blueTerm_" + currentDateTimeString + ".log";
    	
    	mEmulatorView.setFileNameLog( fileName );
    	mEmulatorView.startRecording();

    	mMenuItemStartStopRecording.setTitle(R.string.menu_stop_logging);


        Toast.makeText(getApplicationContext(), getString(R.string.menu_logging_started) + "\n\n" + fileName, Toast.LENGTH_LONG).show();
    }
    
    private void doStopRecording() {
    	mEmulatorView.stopRecording();
    	mMenuItemStartStopRecording.setTitle(R.string.menu_start_logging);
        Toast.makeText(getApplicationContext(), getString(R.string.menu_logging_stopped), Toast.LENGTH_SHORT).show();
    }
    

	private void showAboutDialog() {
		mAboutDialog = new Dialog(BlueTerm.this);
		mAboutDialog.setContentView(R.layout.about);
		mAboutDialog.setTitle( getString( R.string.app_name ) + " " + getString( R.string.app_version ));
		
		Button buttonOpen = (Button) mAboutDialog.findViewById(R.id.buttonDialog);
		buttonOpen.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
		 
				mAboutDialog.dismiss();
			}
		});		
		
		mAboutDialog.show();
    }
}


/**
 * An abstract screen interface. A terminal screen stores lines of text. (The
 * reason to abstract it is to allow different implementations, and to hide
 * implementation details from clients.)
 */
interface Screen {

    /**
     * Set line wrap flag for a given row. Affects how lines are logically
     * wrapped when changing screen size or converting to a transcript.
     */
    void setLineWrap(int row);

    /**
     * Store byte b into the screen at location (x, y)
     *
     * @param x X coordinate (also known as column)
     * @param y Y coordinate (also known as row)
     * @param b ASCII character to store
     * @param foreColor the foreground color
     * @param backColor the background color
     */
    void set(int x, int y, byte b, int foreColor, int backColor);

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line
     * screen, the arguments would be (0, 24).
     *
     * @param topMargin First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     */
    void scroll(int topMargin, int bottomMargin, int foreColor, int backColor);

    /**
     * Block copy characters from one position in the screen to another. The two
     * positions can overlap. All characters of the source and destination must
     * be within the bounds of the screen, or else an InvalidParemeterException
     * will be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w width
     * @param h height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    void blockCopy(int sx, int sy, int w, int h, int dx, int dy);

    /**
     * Block set characters. All characters must be within the bounds of the
     * screen, or else and InvalidParemeterException will be thrown. Typically
     * this is called with a "val" argument of 32 to clear a block of
     * characters.
     *
     * @param sx source X
     * @param sy source Y
     * @param w width
     * @param h height
     * @param val value to set.
     * @param foreColor the foreground color
     * @param backColor the background color
     */
    void blockSet(int sx, int sy, int w, int h, int val, int foreColor, int
            backColor);

    /**
     * Get the contents of the transcript buffer as a text string.
     *
     * @return the contents of the transcript buffer.
     */
    String getTranscriptText();

    /**
     * Resize the screen
     * @param columns
     * @param rows
     */
    void resize(int columns, int rows, int foreColor, int backColor);
}





/**
 * Text renderer interface
 */

interface TextRenderer {
    int getCharacterWidth();
    int getCharacterHeight();
    void drawTextRun(Canvas canvas, float x, float y,
            int lineOffset, char[] text,
            int index, int count, boolean cursor, int foreColor, int backColor);
}

abstract class BaseTextRenderer implements TextRenderer {
    protected int[] mForePaint = {
            0xff000000, // Black
            0xffff0000, // Red
            0xff00ff00, // green
            0xffffff00, // yellow
            0xff0000ff, // blue
            0xffff00ff, // magenta
            0xff00ffff, // cyan
            0xffffffff  // white -- is overridden by constructor
    };
    protected int[] mBackPaint = {
            0xff000000, // Black -- is overridden by constructor
            0xffcc0000, // Red
            0xff00cc00, // green
            0xffcccc00, // yellow
            0xff0000cc, // blue
            0xffff00cc, // magenta
            0xff00cccc, // cyan
            0xffffffff  // white
    };
    protected final static int mCursorPaint = 0xff808080;

    public BaseTextRenderer(int forePaintColor, int backPaintColor) {
        mForePaint[7] = forePaintColor;
        mBackPaint[0] = backPaintColor;

    }
}


class PaintRenderer extends BaseTextRenderer {
    public PaintRenderer(int fontSize, int forePaintColor, int backPaintColor) {
        super(forePaintColor, backPaintColor);
        mTextPaint = new Paint();
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(fontSize);

        mCharHeight = (int) Math.ceil(mTextPaint.getFontSpacing());
        mCharAscent = (int) Math.ceil(mTextPaint.ascent());
        mCharDescent = mCharHeight + mCharAscent;
        mCharWidth = (int) mTextPaint.measureText(EXAMPLE_CHAR, 0, 1);
    }

    public void drawTextRun(Canvas canvas, float x, float y, int lineOffset,
            char[] text, int index, int count,
            boolean cursor, int foreColor, int backColor) {
        if (cursor) {
            mTextPaint.setColor(mCursorPaint);
        } else {
            mTextPaint.setColor(mBackPaint[backColor & 0x7]);
        }
        float left = x + lineOffset * mCharWidth;
        canvas.drawRect(left, y + mCharAscent,
                left + count * mCharWidth, y + mCharDescent,
                mTextPaint);
        boolean bold = ( foreColor & 0x8 ) != 0;
        boolean underline = (backColor & 0x8) != 0;
        if (bold) {
            mTextPaint.setFakeBoldText(true);
        }
        if (underline) {
            mTextPaint.setUnderlineText(true);
        }
        mTextPaint.setColor(mForePaint[foreColor & 0x1]);
        canvas.drawText(text, index, count, left, y, mTextPaint);
        if (bold) {
            mTextPaint.setFakeBoldText(false);
        }
        if (underline) {
            mTextPaint.setUnderlineText(false);
        }
    }

    public int getCharacterHeight() {
        return mCharHeight;
    }

    public int getCharacterWidth() {
        return mCharWidth;
    }


    private Paint mTextPaint;
    private int mCharWidth;
    private int mCharHeight;
    private int mCharAscent;
    private int mCharDescent;
    private static final char[] EXAMPLE_CHAR = {'X'};
    }

/**
 * A multi-thread-safe produce-consumer byte array.
 * Only allows one producer and one consumer.
 */

class ByteQueue {
    public ByteQueue(int size) {
        mBuffer = new byte[size];
    }

    public int getBytesAvailable() {
        synchronized(this) {
            return mStoredBytes;
        }
    }

    public int read(byte[] buffer, int offset, int length)
        throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
            new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return 0;
        }
        synchronized(this) {
            while (mStoredBytes == 0) {
                wait();
            }
            int totalRead = 0;
            int bufferLength = mBuffer.length;
            boolean wasFull = bufferLength == mStoredBytes;
            while (length > 0 && mStoredBytes > 0) {
                int oneRun = Math.min(bufferLength - mHead, mStoredBytes);
                int bytesToCopy = Math.min(length, oneRun);
                System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy);
                mHead += bytesToCopy;
                if (mHead >= bufferLength) {
                    mHead = 0;
                }
                mStoredBytes -= bytesToCopy;
                length -= bytesToCopy;
                offset += bytesToCopy;
                totalRead += bytesToCopy;
            }
            if (wasFull) {
                notify();
            }
            return totalRead;
        }
    }

    public void write(byte[] buffer, int offset, int length)
    throws InterruptedException {
        if (length + offset > buffer.length) {
            throw
                new IllegalArgumentException("length + offset > buffer.length");
        }
        if (length < 0) {
            throw
            new IllegalArgumentException("length < 0");

        }
        if (length == 0) {
            return;
        }
        synchronized(this) {
            int bufferLength = mBuffer.length;
            boolean wasEmpty = mStoredBytes == 0;
            while (length > 0) {
                while(bufferLength == mStoredBytes) {
                    wait();
                }
                int tail = mHead + mStoredBytes;
                int oneRun;
                if (tail >= bufferLength) {
                    tail = tail - bufferLength;
                    oneRun = mHead - tail;
                } else {
                    oneRun = bufferLength - tail;
                }
                int bytesToCopy = Math.min(oneRun, length);
//                 assuming this next line is writing the data to screen
                System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy);
                offset += bytesToCopy;
                mStoredBytes += bytesToCopy;
                length -= bytesToCopy;
            }
            if (wasEmpty) {
                notify();
            }
        }
    }

    private byte[] mBuffer;
    private int mHead;
    private int mStoredBytes;
}




/**
 * An ASCII key listener. Supports control characters and escape. Keeps track of
 * the current state of the alt, shift, and control keys.
 */
class TermKeyListener {
    /**
     * The state engine for a modifier key. Can be pressed, released, locked,
     * and so on.
     *
     */
    private class ModifierKey {

        private int mState;

        private static final int UNPRESSED = 0;

        private static final int PRESSED = 1;

        private static final int RELEASED = 2;

        private static final int USED = 3;

        private static final int LOCKED = 4;

        /**
         * Construct a modifier key. UNPRESSED by default.
         *
         */
        public ModifierKey() {
            mState = UNPRESSED;
        }

        public void onPress() {
            switch (mState) {
            case PRESSED:
                // This is a repeat before use
                break;
            case RELEASED:
                mState = LOCKED;
                break;
            case USED:
                // This is a repeat after use
                break;
            case LOCKED:
                mState = UNPRESSED;
                break;
            default:
                mState = PRESSED;
                break;
            }
        }

        public void onRelease() {
            switch (mState) {
            case USED:
                mState = UNPRESSED;
                break;
            case PRESSED:
                mState = RELEASED;
                break;
            default:
                // Leave state alone
                break;
            }
        }

        public void adjustAfterKeypress() {
            switch (mState) {
            case PRESSED:
                mState = USED;
                break;
            case RELEASED:
                mState = UNPRESSED;
                break;
            default:
                // Leave state alone
                break;
            }
        }

        public boolean isActive() {
            return mState != UNPRESSED;
        }
    }

    private ModifierKey mAltKey = new ModifierKey();

    private ModifierKey mCapKey = new ModifierKey();

    private ModifierKey mControlKey = new ModifierKey();

    /**
     * Construct a term key listener.
     *
     */
    public TermKeyListener() {
    }

    public void handleControlKey(boolean down) {
        if (down) {
            mControlKey.onPress();
        } else {
            mControlKey.onRelease();
        }
    }

    public int mapControlChar(int ch) {
        int result = ch;
        if (mControlKey.isActive()) {
            // Search is the control key.
            if (result >= 'a' && result <= 'z') {
                result = (char) (result - 'a' + '\001');
            } else if (result == ' ') {
                result = 0;
            } else if ((result == '[') || (result == '1')) {
                result = 27;
            } else if ((result == '\\') || (result == '.')) {
                result = 28;
            } else if ((result == ']') || (result == '0')) {
                result = 29;
            } else if ((result == '^') || (result == '6')) {
                result = 30; // control-^
            } else if ((result == '_') || (result == '5')) {
                result = 31;
            }
        }

        if (result > -1) {
            mAltKey.adjustAfterKeypress();
            mCapKey.adjustAfterKeypress();
            mControlKey.adjustAfterKeypress();
        }
        return result;
    }

    /**
     * Handle a keyDown event.
     *
     * @param keyCode the keycode of the keyDown event
     * @return the ASCII byte to transmit to the pseudo-teletype, or -1 if this
     *         event does not produce an ASCII byte.
     */
    public int keyDown(int keyCode, KeyEvent event) {
        int result = -1;
        switch (keyCode) {
        case KeyEvent.KEYCODE_ALT_RIGHT:
        case KeyEvent.KEYCODE_ALT_LEFT:
            mAltKey.onPress();
            break;

        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            mCapKey.onPress();
            break;

        case KeyEvent.KEYCODE_ENTER:
            // Convert newlines into returns. The vt100 sends a
            // '\r' when the 'Return' key is pressed, but our
            // KeyEvent translates this as a '\n'.
            result = '\r';
            break;

        case KeyEvent.KEYCODE_DEL:
            // Convert DEL into 127 (instead of 8)
            result = 127;
            break;

        default: {
            result = event.getUnicodeChar(
                   (mCapKey.isActive() ? KeyEvent.META_SHIFT_ON : 0) |
                   (mAltKey.isActive() ? KeyEvent.META_ALT_ON : 0));
            break;
            }
        }

        result = mapControlChar(result);

        return result;
    }

    /**
     * Handle a keyUp event.
     *
     * @param keyCode the keyCode of the keyUp event
     */
    public void keyUp(int keyCode) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_ALT_LEFT:
        case KeyEvent.KEYCODE_ALT_RIGHT:
            mAltKey.onRelease();
            break;
        case KeyEvent.KEYCODE_SHIFT_LEFT:
        case KeyEvent.KEYCODE_SHIFT_RIGHT:
            mCapKey.onRelease();
            break;
        default:
            // Ignore other keyUps
            break;
        }
    }
}
