package com.example.auser.bt_control2;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.InputStreamReader;

import static java.lang.String.valueOf;

public class EditActivity extends Activity {

    private TextView dataTextView ;
    private Button cleanBtn, sendBtn;
    private EditText sendEdit;
    private BluetoothAdapter btAdapter;
    private Context context;
    private String remoteDeviceInfo;
    private BTChatService mChatService = null;
    private String remoteMacAddress;
    private String mConnectedDevice =null;

    private static final String TAG = "BT_Edit";

    private final int groupMenuID=1 ;
    private final int File_1 =0;
    private final int File_2 =1;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_activity);

        context = this;
        //  Dispaly BT data
        dataTextView = (TextView) findViewById(R.id.data_textView);
        dataTextView.setText("");
        Log.d(TAG, "Edit_OnCreate_start");

        cleanBtn = (Button)findViewById(R.id.clean_btn);
        cleanBtn.setOnClickListener(new BtnOnClickListener());

        sendBtn = (Button)findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(new BtnOnClickListener());

        sendEdit = (EditText) findViewById(R.id.send_editText);
        sendEdit.setText("");

        sendEdit.setOnEditorActionListener(textEditListen);

        //get data from MainActivity
        Intent intent = getIntent();
        remoteDeviceInfo=intent.getStringExtra("remoteDevice");

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        // Always cancel discovery because it will slow down a connection
        btAdapter.cancelDiscovery();

        // Initialize the BluetoothChatService to perform bluetooth connection mode
        mChatService = new BTChatService(context , mHandler);

        if(remoteDeviceInfo!=null) {         // set BT in cliet mode
            Log.d(TAG, "BT Cliet mode");
            dataTextView.append("BT module in Client mode. \n");
            String deviceMsg = remoteDeviceInfo.substring(10) ;
            Log.d(TAG, deviceMsg);
            dataTextView.append("Connecting to remote BT device :  \n" + deviceMsg + "\n\n");

            // Get the device MAC address
            remoteMacAddress = remoteDeviceInfo.substring(remoteDeviceInfo.length() - 17) ;
            Log.d(TAG, remoteMacAddress);

            // Get the remote BluetoothDevice object
            BluetoothDevice device = btAdapter.getRemoteDevice(remoteMacAddress);

            // Attempt to connect to the device
            mChatService.connect(device);

        }else {
            // set BT in Server mode
            Log.d(TAG, "BT Server mode");
            dataTextView.append("Make BT module in Server mode. \n");
            // Start the Bluetooth chat services
            mChatService.serverStart();

        }


    } // end of onCreate()


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            Log.d(TAG,"EditActivity onDestry()");
            mChatService.stop();//停止所有BT關於的東西
            mChatService=null;
        }
    }


    // if TextEdit string was ended by enter key then send data out
    private TextView.OnEditorActionListener textEditListen = new TextView.OnEditorActionListener(){
        //  EditorInfo.IME_NULL if being called due to the enter key being pressed
        public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
           Log.d(TAG, "enter key check : "+valueOf(actionId));

           if (actionId == EditorInfo.IME_ACTION_DONE ) {
                Log.d(TAG, "enter key pressed");
                String message = textView.getText().toString();
                dataTextView.append(">> " + message + "\n");     //display on TextView
                sendMessage(message);//送出所有打字的資料
               // clear the edit text field
                sendEdit.setText("");
            }
            return true;
        }
    };

    // Check if any button is  pressed
    private class BtnOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            switch(view.getId()) {

                case R.id.clean_btn:           // clean display TextView
                    dataTextView.setText("");
                    Toast.makeText(EditActivity.this, "Clean display", Toast.LENGTH_SHORT).show();
                    break;

                case R.id.send_btn:            // press Discoverable button to make BT module discoverable for 180 sec
                    String message= sendEdit.getText().toString();
                    dataTextView.append(">> " + message + "\n");   //display on TextView
                    // Send a message using content of the edit text widget
                    sendMessage(message);
                    // clear the edit text field
                    sendEdit.setText("");
                    break;
            }
        }

    }


    private  void sendMessage(String message){//若不是在連結的狀態時,資料無法送出,則顯示一個Toast,顯示目前程式跑到那些地方,方便debug
        int mState=mChatService.getState();
        Log.d(TAG,"btStage in sendMessage" + valueOf(mState));
        if (mState!=BTChatService.STATE_CONNECTED){
            Toast.makeText(context,"Bluetooth device is not connected",Toast.LENGTH_SHORT).show();
            return;
        }else{
            if (message.length()>0){
                byte[] send=message.getBytes();
                mChatService.BTWrite(send);//把資料送到Booth
            }
        }

    }

    private  final  Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            //收到的資料開頭會有"remote",送出的資料會有"<<開頭"
            switch (msg.what){
                case Constants.MESSAGE_READ:
                    byte[] readBuf=(byte[]) msg.obj;
                    String readMessage=new String(readBuf,0,msg.arg1);
                    dataTextView.append("remote :" +readMessage + "\n");
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    mConnectedDevice=msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(context,"connected to " +mConnectedDevice,Toast.LENGTH_SHORT);
                    break;
                case Constants.MESSAGE_TOAST:
                    Toast.makeText(context,msg.getData().getString(Constants.TOAST),Toast.LENGTH_SHORT).show();
                    break;


            }
        }
    };



   @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(groupMenuID,File_1,menu.NONE,"Send File 1");
        menu.add(groupMenuID,File_2,menu.NONE,"Send File 2");
        return true;
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        InputStream inputFile=null;
        InputStreamReader fileChar;
        StringBuilder fileBuffer=new StringBuilder();
        char[] buffer =new char[200];//做為暫存讀出來的資料
        if (item.getGroupId()==groupMenuID){
            switch (item.getItemId()){
                case File_1:
                    Toast.makeText(context,"Send file 1",Toast.LENGTH_SHORT).show();
                    try{
                        inputFile=context.getResources().openRawResource(R.raw.file_1);
                        fileChar =new InputStreamReader(inputFile,"UTF-8");
                        while ((fileChar.read(buffer))!=-1){//若讀進來的檔案已讀取完成則結束
                            fileBuffer.append(new String(buffer));
                        }
                        String message=fileBuffer.toString();
                        dataTextView.append(">>" + message +"\n\n");
                    }catch(Exception e){
                        Log.d("TAG","Read File 1 failed" + e);
                    }finally {
                        try{
                            if (inputFile!=null) inputFile.close();
                        }catch(Exception e1){

                        }
                    }
                    break;
                case File_2:
                    Toast.makeText(context,"Send file 2",Toast.LENGTH_SHORT).show();
                    try{
                        inputFile=context.getResources().openRawResource(R.raw.file_2);
                        fileChar =new InputStreamReader(inputFile,"UTF-8");
                        while ((fileChar.read(buffer))!=-1){//若讀進來的檔案已讀取完成則結束
                            fileBuffer.append(new String(buffer));
                        }
                        String message=fileBuffer.toString();
                        dataTextView.append(">>" + message +"\n\n");
                    }catch(Exception e){
                        Log.d("TAG","Read File 1 failed" + e);
                    }finally {
                        try{
                            if (inputFile!=null) inputFile.close();
                        }catch(Exception e1){

                        }
                    }
                    break;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
