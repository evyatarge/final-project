package es.pymasde.blueterm;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * Created by evyatarg on 6/27/2016.
 *
 * A view on a transcript and a terminal emulator. Displays the text of the
 * transcript and the current cursor position of the terminal emulator.
 *
 */

public class EmulatorView extends View implements GestureDetector.OnGestureListener {


    /**
     * We defer some initialization until we have been layed out in the view
     * hierarchy. The boolean tracks when we know what our size is.
     */
    private boolean mKnownSize;

    /**
     * Our transcript. Contains the screen and the transcript.
     */
    private TranscriptScreen mTranscriptScreen;

    /**
     * Number of rows in the transcript.
     */
    private static final int TRANSCRIPT_ROWS = 500;

    /**
     * Total width of each character, in pixels
     */
    private int mCharacterWidth;

    /**
     * Total height of each character, in pixels
     */
    private int mCharacterHeight;

    /**
     * Used to render text
     */
    private TextRenderer mTextRenderer;

    /**
     * Text size. Zero means 4 x 8 font.
     */
    private int mTextSize;

    /**
     * Foreground color.
     */
    private int mForeground;

    /**
     * Background color.
     */
    private int mBackground;

    /**
     * Used to paint the cursor
     */
    private Paint mCursorPaint;

    private Paint mBackgroundPaint;

    /**
     * Our terminal emulator. We use this to get the current cursor position.
     */
    private TerminalEmulator mEmulator;

    private int mWidth;
    private int mHeight;

    /**
     * The number of rows of text to display.
     */
    private int mRows;

    /**
     * The number of columns of text to display.
     */
    private int mColumns;

    /**
     * The number of columns that are visible on the display.
     */

    private int mVisibleColumns;

    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0
     */
    private int mTopRow;

    private int mLeftColumn;

    private ByteQueue mByteQueue;

    /**
     * Used to temporarily hold data received from the remote process. Allocated
     * once and used permanently to minimize heap thrashing.
     */
    private byte[] mReceiveBuffer;

    /**
     * Our private message id, which we use to receive new input from the
     * remote process.
     */
    public static final int UPDATE = 1;

    /////////////////////////////////////////////////       MY VARIABLES        ///////////////////
    private static final int DIGITS = 3;
    // the minimum length of line format of data, that can be read and parse from the sensors
    private static final int LINE_LENGTH = (BlueTerm.numOfSensors*DIGITS)+BlueTerm.numOfSensors+1;
    // the min and max values of sensors. this is used to normalized the values
    public static final double MIN_SENSOR_VAL = 600;
    private static final double MAX_SENSOR_VAL = 900;
    // after we are taking the values from the sensors, we want to present the data in some range of normalized
    private static final double MAX_RANGE = 100;
    private static final double MIN_RANGE = 0;

    final int NUM_OF_SENSORS = BlueTerm.numOfSensors;
    //the current eval of the sensors
    int[] eval = new int[NUM_OF_SENSORS];

    //this represent the percentage and colors of the sensors current state
    int[] percents = new int[NUM_OF_SENSORS];
    String[] colors = new String[NUM_OF_SENSORS];

    public static String RED = "#f00000", YELLOW="#e8f000", GREEN="#04f000", WHITE="#ffffff";
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private String mFileNameLog;
    private Date mOldTimeLog = new Date();
    private boolean mRecording = false;

    private GestureDetector mGestureDetector;
    private float mScrollRemainder;
    private TermKeyListener mKeyListener;

    private BlueTerm mBlueTerm;

    private Runnable mCheckSize = new Runnable() {
        public void run() {
            updateSize();
            mHandler.postDelayed(this, 100);
        }
    };

    /**
     * Our message handler class. Implements a periodic callback.
     */
    protected final Handler mHandler = new Handler() {
        /**
         * Handle the callback message. Call our enclosing class's update
         * method.
         *
         * @param msg The callback message.
         */
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == UPDATE) {
                update();

                //enter here methods to update the UI using the names of the components of the screen (id)
                // [because changes need to be inside a handler]

                setEvaluetedToPercentage();
                setPercentageColor();

                for(int s = 0; s< NUM_OF_SENSORS; s++){
                    BlueTerm.sensors.get(s).setText("area "+(s+1)+" get "+percents[s]+"% pressure");
                }

            }
        }
    };


    private void setPercentageColor() {
        for (int i = 0; i < NUM_OF_SENSORS; i++){
            mBlueTerm.sensors.get(i).setBackgroundColor(Color.parseColor(colors[i]));
            if(colors[i].equals(RED)){
                mBlueTerm.sensors.get(i).setTextColor(Color.parseColor(WHITE));
            }
            else{
                mBlueTerm.sensors.get(i).setTextColor(Color.parseColor(RED));
            }
        }
    }

    private void setEvaluetedToPercentage() {
        int total_100percnt = get100Percent();
        if(total_100percnt != 0){
            for(int i=0; i< eval.length; i++){
                percents[i] = (eval[i]*100)/total_100percnt;
            }
        }

        setPercentToColor(percents,colors);
    }

    private void setPercentToColor(int[] percents, String[] colorsToSensorsByPercent) {

        for(int i = 0; i<percents.length; i++){
//            if(percents[i]==0){continue;}//TODO - maybe set this with color and text that say we have here some problem
            if(percents[i] > 34){
                colorsToSensorsByPercent[i] = RED;
            }
            else if(percents[i] < 32){
                colorsToSensorsByPercent[i] = GREEN;
            }
            else{
                colorsToSensorsByPercent[i] = YELLOW;
            }
        }
    }

    private int get100Percent() {
        int total100percnt = 0;
        for(int i=0; i< eval.length; i++){
            total100percnt += eval[i];
        }
        return total100percnt;
    }


    public EmulatorView(Context context) {
        super(context);
        commonConstructor(context);
    }

    public void onResume() {
        updateSize();
        mHandler.postDelayed(mCheckSize, 100);
    }

    public void onPause() {
        mHandler.removeCallbacks(mCheckSize);
    }


    public void register(TermKeyListener listener) {
        mKeyListener = listener;
    }

    public void setColors(int foreground, int background) {
        mForeground = foreground;
//        mBackground = background;
        updateText();
    }

    public String getTranscriptText() {
        return mEmulator.getTranscriptText();
    }

    public void resetTerminal() {
        mEmulator.reset();
        invalidate();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = EditorInfo.TYPE_NULL;
        return new BaseInputConnection(this, false) {

            @Override
            public boolean beginBatchEdit() {
                return true;
            }

            @Override
            public boolean clearMetaKeyStates(int states) {
                return true;
            }

            @Override
            public boolean commitCompletion(CompletionInfo text) {
                return true;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                sendText(text);
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                return true;
            }

            @Override
            public boolean endBatchEdit() {
                return true;
            }

            @Override
            public boolean finishComposingText() {
                return true;
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                return 0;
            }

            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest request,
                                                  int flags) {
                return null;
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                return null;
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                return null;
            }

            @Override
            public boolean performEditorAction(int actionCode) {
                if(actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    // The "return" key has been pressed on the IME.
                    sendText("\n");
                    return true;
                }
                return false;
            }

            @Override
            public boolean performContextMenuAction(int id) {
                return true;
            }

            @Override
            public boolean performPrivateCommand(String action, Bundle data) {
                return true;
            }

            /*
                        @Override
                        public boolean sendKeyEvent(KeyEvent event) {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                switch(event.getKeyCode()) {
                                case KeyEvent.KEYCODE_DEL:
                                    sendChar(127);
                                    break;
                                }
                            }
                            return true;
                        }
            */
            // Code from
            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Some keys are sent here rather than to commitText.
                    // In particular, del and the digit keys are sent here.
                    // (And I have reports that the HTC Magic also sends Return here.)
                    // As a bit of defensive programming, handle every
                    // key with an ASCII meaning.
                    int keyCode = event.getKeyCode();
                    if (keyCode >= 0 && keyCode < KEYCODE_CHARS.length()) {
                        char c = KEYCODE_CHARS.charAt(keyCode);
                        if (c > 0) {
                            sendChar(c);
                        } else {
                            // Handle IME arrow key events
                            switch (keyCode) {
                                case KeyEvent.KEYCODE_DPAD_UP:      // Up Arrow
                                case KeyEvent.KEYCODE_DPAD_DOWN:    // Down Arrow
                                case KeyEvent.KEYCODE_DPAD_LEFT:    // Left Arrow
                                case KeyEvent.KEYCODE_DPAD_RIGHT:   // Right Arrow
                                    super.sendKeyEvent(event);
                                    break;
                                default:
                                    break;
                            }  // switch (keyCode)
                        }
                    }
                }
                return true;
            }

            private final String KEYCODE_CHARS =
                    "\000\000\000\000\000\000\000" + "0123456789*#"
                            + "\000\000\000\000\000\000\000\000\000\000"
                            + "abcdefghijklmnopqrstuvwxyz,."
                            + "\000\000\000\000"
                            + "\011 "   // tab, space
                            + "\000\000\000" // sym .. envelope
                            + "\015\177" // enter, del
                            + "`-=[]\\;'/@"
                            + "\000\000\000"
                            + "+";

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                return true;
            }

            @Override
            public boolean setSelection(int start, int end) {
                return true;
            }

            private void sendChar(int c) {
                try {
                    mapAndSend(c);
                } catch (IOException ex) {
                }
            }
            private void sendText(CharSequence text) {
                int n = text.length();
                try {
                    for(int i = 0; i < n; i++) {
                        char c = text.charAt(i);
                        mapAndSend(c);
                    }
                } catch (IOException e) {
                }
            }

            private void mapAndSend(int c) throws IOException {
                byte[] mBuffer = new byte[1];
                mBuffer[0] = (byte)mKeyListener.mapControlChar(c);

                mBlueTerm.send(mBuffer);
            }
        };
    }

    public void write(byte[] buffer, int length) {
        try {
            mByteQueue.write(buffer, 0, length);

        } catch (InterruptedException e) {
        }
        mHandler.sendMessage( mHandler.obtainMessage(UPDATE));
    }

    public boolean getKeypadApplicationMode() {
        return mEmulator.getKeypadApplicationMode();
    }

    public EmulatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmulatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(R.styleable.EmulatorView);
        try {
            // initializeScrollbars(TypedArray)
            Method initializeScrollbars = android.view.View.class.getDeclaredMethod("initializeScrollbars", TypedArray.class);
            initializeScrollbars.invoke(this, a);
        } catch (Exception e) {
            e.printStackTrace();
        }
        a.recycle();
        commonConstructor(context);
    }

    private void commonConstructor(Context context) {
        mTextRenderer = null;
        mCursorPaint = new Paint();
        mCursorPaint.setARGB(255,128,128,128);
        mBackgroundPaint = new Paint();
        mTopRow = 0;
        mLeftColumn = 0;
        mGestureDetector = new GestureDetector(context, this, null);
        mGestureDetector.setIsLongpressEnabled( true );
        setVerticalScrollBarEnabled(true);
    }

    @Override
    protected int computeVerticalScrollRange() {
        if (mTranscriptScreen == null)
            return 0;

        return mTranscriptScreen.getActiveRows();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mRows;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        if (mTranscriptScreen == null)
            return 0;

        return mTranscriptScreen.getActiveRows() + mTopRow - mRows;
    }


    public void initialize(BlueTerm blueTerm) {
        mBlueTerm = blueTerm;
        mTextSize = 30;
        mForeground = Color.TRANSPARENT;
//        mBackground = BlueTerm.WHITE;
        updateText();
        mReceiveBuffer = new byte[4 * 1024];
        mByteQueue = new ByteQueue(4 * 1024);
    }

    /**
     * Accept a sequence of bytes (typically from the pseudo-tty) and process
     * them.
     *
     * @param buffer a byte array containing bytes to be processed
     * @param base the index of the first byte in the buffer to process
     * @param length the number of bytes to process
     */
    public void append(byte[] buffer, int base, int length) {
        mEmulator.append(buffer, base, length);
        ensureCursorVisible();
        invalidate();
    }

    /**
     * Page the terminal view (scroll it up or down by delta screenfulls.)
     *
     * @param delta the number of screens to scroll. Positive means scroll down,
     *        negative means scroll up.
     */
    public void page(int delta) {
        mTopRow =
                Math.min(0, Math.max(-(mTranscriptScreen
                        .getActiveTranscriptRows()), mTopRow + mRows * delta));
        invalidate();
    }

    /**
     * Page the terminal view horizontally.
     *
     * @param deltaColumns the number of columns to scroll. Positive scrolls to
     *        the right.
     */
    public void pageHorizontal(int deltaColumns) {
        mLeftColumn =
                Math.max(0, Math.min(mLeftColumn + deltaColumns, mColumns
                        - mVisibleColumns));
        invalidate();
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns
     *
     * @param fontSize the new font size, in pixels.
     */
    public void setTextSize(int fontSize) {
        mTextSize = fontSize;
        updateText();
    }

    // Begin GestureDetector.OnGestureListener methods

    public boolean onSingleTapUp(MotionEvent e) {

        mBlueTerm.toggleKeyboard();
        return true;
    }

    public void onLongPress(MotionEvent e) {
        mBlueTerm.doOpenOptionsMenu();
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        distanceY += mScrollRemainder;
        int deltaRows = (int) (distanceY / mCharacterHeight);
        mScrollRemainder = distanceY - deltaRows * mCharacterHeight;
        mTopRow = Math.min(0, Math.max(-(mTranscriptScreen.getActiveTranscriptRows()), mTopRow + deltaRows));

        awakenScrollBars();
        invalidate();

        return true;
    }

    public void onSingleTapConfirmed(MotionEvent e) {
    }

    public boolean onJumpTapDown(MotionEvent e1, MotionEvent e2) {
        // Scroll to bottom
        mTopRow = 0;
        invalidate();
        return true;
    }

    public boolean onJumpTapUp(MotionEvent e1, MotionEvent e2) {
        // Scroll to top
        mTopRow = -mTranscriptScreen.getActiveTranscriptRows();
        invalidate();
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // TODO: add animation man's (non animated) fling
        mScrollRemainder = 0.0f;
        onScroll(e1, e2, 2 * velocityX, -2 * velocityY);
        return true;
    }

    public void onShowPress(MotionEvent e) {
    }

    public boolean onDown(MotionEvent e) {
        mScrollRemainder = 0.0f;
        return true;
    }

    // End GestureDetector.OnGestureListener methods

    @Override public boolean onTouchEvent(MotionEvent ev) {
        return mGestureDetector.onTouchEvent(ev);
    }

    private void updateText() {
        if (mTextSize > 0) {
            mTextRenderer = new PaintRenderer(mTextSize, mForeground, mBackground);
        }
        mBackgroundPaint.setColor(Color.TRANSPARENT);
        mCharacterWidth = mTextRenderer.getCharacterWidth();
        mCharacterHeight = mTextRenderer.getCharacterHeight();

        if (mKnownSize) {
            //updateSize(getWidth(), getHeight());
            updateSize();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        //updateSize(w, h);
        if (!mKnownSize)
            mKnownSize = true;

        updateSize();
    }

    private void updateSize(int w, int h) {
        if(w <= 0 || h <= 0)
            return;

        mColumns = w / mCharacterWidth;
        mRows = h / mCharacterHeight;

        if (mTranscriptScreen != null) {
            mEmulator.updateSize(mColumns, mRows);
        } else {
            mTranscriptScreen = new TranscriptScreen(mColumns, TRANSCRIPT_ROWS, mRows, 0, 7);
            mEmulator = new TerminalEmulator(mTranscriptScreen, mColumns, mRows);
        }

        // Reset our paging:
        mTopRow = 0;
        mLeftColumn = 0;

        this.layout(0, 0, w, h);
        invalidate();
    }

    void updateSize() {
        Rect visibleRect;

        if (mBlueTerm == null)
            return;

        if (mKnownSize) {
            visibleRect = new Rect();
            getWindowVisibleDisplayFrame(visibleRect);
            int w = visibleRect.width();
            int h = visibleRect.height() - mBlueTerm.getTitleHeight() - 2;
            if (w != mWidth || h != mHeight) {
                mWidth = w;
                mHeight = h;
                updateSize( w, h );
            }
        }
    }

    /**
     * Look for new input from the ptty, send it to the terminal emulator.
     */
    private void update() {
        int bytesAvailable = mByteQueue.getBytesAvailable();
        int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
        try {
            int bytesRead = mByteQueue.read(mReceiveBuffer, 0, bytesToRead);
            String stringRead = new String(mReceiveBuffer, 0, bytesRead);
// TODO - from here its my changes
            parseSensorsData(stringRead);
//TODO - until here its my added main code
            append(mReceiveBuffer, 0, bytesRead);

            if(mRecording) {
                this.writeLog( stringRead );
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("read sensors data interrupted with the following message: "+e.getMessage());
        }
    }


    private void parseSensorsData(String stringRead) {

        if(stringRead.length() < LINE_LENGTH){
            return;
        }

        StringTokenizer lineDataToken = new StringTokenizer(stringRead);

        int currentSensNum = 0;

        while (lineDataToken.hasMoreTokens()){

            String currentSensorData = lineDataToken.nextToken();

            try {
                int sensDataValue = Integer.parseInt(currentSensorData);
                if (sensDataValue >= MIN_SENSOR_VAL && sensDataValue <= MAX_SENSOR_VAL) {
                    boolean isTheFirstEnter = eval[currentSensNum]==0;
                    eval[currentSensNum] = eval[currentSensNum] + sensDataValue;
                    if(!isTheFirstEnter){
                        eval[currentSensNum] = eval[currentSensNum]/2; //TODO - this is a temporary calculation for the sensors data instead of a real average
//                        eval[currentSensNum] = normalizeEvaluationToRange(eval[currentSensNum]);//TODO - this is a problem due to the fact that the eval is accumulated in itself
                    }
                }
            }
            catch (NumberFormatException nfe){
                nfe.printStackTrace();
            }
            finally {
                currentSensNum = (currentSensNum+1)%BlueTerm.numOfSensors;
            }

        }
    }

    private int normalizeEvaluationToRange(int current) {
        if(current<MIN_SENSOR_VAL){
            return current;
        }
        double realResult = (((double)current-MIN_SENSOR_VAL)/MIN_SENSOR_VAL)*(MAX_RANGE-MIN_RANGE)+MIN_RANGE;
        int result = (int) realResult;
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        if (mCharacterWidth == 0)
            return;

        canvas.drawRect(0, 0, 0, 0, mBackgroundPaint);
        mVisibleColumns = w / mCharacterWidth;
        float x = -mLeftColumn * mCharacterWidth;
        float y = mCharacterHeight;
        int endLine = mTopRow + mRows;
        int cx = mEmulator.getCursorCol();
        int cy = mEmulator.getCursorRow();
        for (int i = mTopRow; i < endLine; i++) {
            int cursorX = -1;
            if (i == cy) {
                cursorX = cx;
            }
            //TODO - this is the text draw for every row of sensors read on screen
//            mTranscriptScreen.drawText(i, canvas, x, y, mTextRenderer, cursorX);
            y += mCharacterHeight;
        }
    }

    private void ensureCursorVisible() {
        mTopRow = 0;
        if (mVisibleColumns > 0) {
            int cx = mEmulator.getCursorCol();
            int visibleCursorX = mEmulator.getCursorCol() - mLeftColumn;
            if (visibleCursorX < 0) {
                mLeftColumn = cx;
            } else if (visibleCursorX >= mVisibleColumns) {
                mLeftColumn = (cx - mVisibleColumns) + 1;
            }
        }
    }

    public void setFileNameLog( String fileNameLog ) {
        mFileNameLog = fileNameLog;
    }

    public void startRecording() {
        mRecording = true;
    }

    public void stopRecording() {
        mRecording = false;
    }

    public boolean writeLog(String buffer) {
        String state = Environment.getExternalStorageState();
        File logFile = new File ( mFileNameLog );

        if (Environment.MEDIA_MOUNTED.equals(state)) {

            try {
                FileOutputStream f = new FileOutputStream( logFile, true );

                PrintWriter pw = new PrintWriter(f);
                pw.print( buffer );
                pw.flush();
                pw.close();

                f.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            this.stopRecording();
            return false;
        } else {
            this.stopRecording();
            return false;
        }

        return true;
    }

    public void setIncomingEoL_0D( int eol ) {
        if ( mEmulator != null ) {
            mEmulator.setIncomingEoL_0D( eol );
        }
    }

    public void setIncomingEoL_0A( int eol ) {
        if ( mEmulator != null ) {
            mEmulator.setIncomingEoL_0A( eol );
        }
    }
}

