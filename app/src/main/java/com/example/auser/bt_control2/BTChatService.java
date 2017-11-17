package com.example.auser.bt_control2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.UUID;

/**
 * Created by auser on 2017/11/17.
 */

public class BTChatService {
    private final BluetoothAdapter btAdapter;
    private static final int STATE_NORMAL = 0;//定義BOOTH是扮演什麼角色的常數
    private static final int STATE_WaitingConnection = 1;//定義BOOTH是扮演什麼角色的常數
    private static final int STATE_CONNECTING = 2;//定義BOOTH是扮演什麼角色的常數
    public static final int STATE_CONNECTED = 3;//定義BOOTH是扮演什麼角色的常數
    private static final int STATE_STOP = 4;//定義BOOTH是扮演什麼角色的常數
    private final Handler btHanlder;
    public int btState;//代表各種不同的執行緒目前在什麼階段
    public static final UUID UUID_String = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");//BOOTH的標準密碼
    //    private static final UUID UUID_String= UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");//可以連BOOTH SPP的應用程式…
    private static final String TAG = "BT_Chat";

    private AcceptThread btAcceptTread;
    private ConnectingThread btConnectingThread;
    private ConnectedThread btConnectedThread;




    public BTChatService(Context context, Handler handler) {//handler預備用來傳送message que
        //先得BOOTH控制權
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        btState = STATE_NORMAL;//記錄目前在BOOTH是扮演什麼角色1server2client3正在扮演傳送的工作
        btHanlder = handler;

    }

    //取得btStage的狀況,Activity會去呼叫到,所以public
    public int getState() {
        return btState;
    }

    //進入Server Mode時,會有一個ServerStart的方法
    public void serverStart() {
        Log.d(TAG, "Enter server mode. ");

        if (btConnectingThread != null) {
            btConnectingThread.cancel();
            btConnectingThread = null;
        }
        if (btConnectedThread != null) {
            btConnectedThread.cancel();
            btConnectedThread = null;
        }
        if (btAcceptTread != null) {//尚未進入server mode,所以要建立一個thread

            btAcceptTread=new AcceptThread();
            btAcceptTread.start();
        }
    }

    //做連結時,會去呼叫connect方法,傳入連結資料時的對方資料,放在BluetoothDevice//1
    public void connect(BluetoothDevice device) {
        //印出傳進來的內容,可以知到連到那裏去
        Log.d(TAG, "connect to :" + device);

        if (btConnectingThread != null) {
            btConnectingThread.cancel();
            btConnectingThread = null;
        }
        if (btConnectedThread != null) {
            btConnectedThread.cancel();
            btConnectedThread = null;
        }
        if (btAcceptTread != null) {
            btAcceptTread.cancel();
            btAcceptTread = null;
        }
        btConnectingThread=new ConnectingThread(device);
        btConnectingThread.start();
    }

    //edit結束時,回上一頁時要關掉很多thread,synchronized:要等這個方法完全做完後才可以中斷.中間不能停止,被插隊
    public synchronized void stop() {
        Log.d(TAG, "Stop all threads;");
        if (btConnectingThread != null) {
            btConnectingThread.cancel();
            btConnectingThread = null;
        }
        if (btConnectedThread != null) {
            btConnectedThread.cancel();
            btConnectedThread = null;
        }
        if (btAcceptTread != null) {
            btAcceptTread.cancel();
            btAcceptTread = null;
        }
        btState=STATE_STOP;

    }

    public void BTWrite(byte[] out) {
        if (btState != STATE_CONNECTED) return;//若不處於連結的模式時,則不送出資料,只顯示在自己的手機
        btConnectedThread.write(out);
    }

    private class AcceptThread extends Thread {
        private BluetoothServerSocket BTServerSocket;
        BluetoothServerSocket tempSocket;
        BluetoothSocket btSocket;
        private BluetoothDevice device;

        public AcceptThread() {
            try {
                tempSocket = btAdapter.listenUsingRfcommWithServiceRecord("BT_Chat", UUID_String);
                Log.d(TAG, "Get BT ServerSocket OK");
            } catch (IOException e) {
                Log.d(TAG, " Get BT ServerSocket failed");
            }
            BTServerSocket = tempSocket;
            btState = STATE_WaitingConnection;//若建立AcceptThread後就開始等待連結
        }


        public void run() {
            //若不是在連結這個狀態時,才可以做下面的事情
            while ((btState != STATE_CONNECTED)) {
                try {
                    btSocket = BTServerSocket.accept();
                } catch (IOException e) {
                    Log.d(TAG, "Get BT SOCKET fail" + e);
                    break;
                }


            if (btSocket != null) {
                switch (btState) {
                    case STATE_WaitingConnection:
                    case STATE_CONNECTING:
                        device = btSocket.getRemoteDevice();

                        if (btConnectingThread != null) {
                            btConnectingThread.cancel();
                            btConnectingThread = null;
                        }
                        if (btConnectedThread != null) {
                            btConnectedThread.cancel();
                            btConnectedThread = null;
                        }
                        if (btAcceptTread != null) {
                            btAcceptTread.cancel();
                            btAcceptTread = null;
                        }
                        btConnectedThread = new ConnectedThread(btSocket);
                        btConnectedThread.start();

                        Message msg = btHanlder.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
                        Bundle bundle = new Bundle();
                        bundle.putString(Constants.DEVICE_NAME, device.getName());
                        msg.setData(bundle);
                        btHanlder.sendMessage(msg);
                        break;
                    case STATE_NORMAL:
                    case STATE_CONNECTED:
                        try {
                            btSocket.close();
                        } catch (IOException e) {
                            Log.d(TAG, "close failed");
                        }
                        break;
                }
            }
        }
        }

        public void cancel() {
            try {
                BTServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket btSocket;
        private final InputStream btInputStream;
        private final OutputStream btOutputStream;

        //被呼叫是要帶給呼叫者(server,或client)bluetooth使用權限
        public ConnectedThread(BluetoothSocket socket) {
            btSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmoOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmoOut = socket.getOutputStream();
            } catch (IOException e) {
            }
            btInputStream = tmpIn;
            btOutputStream = tmoOut;
            btState = STATE_CONNECTED;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytesReadLength;
            while (btState == STATE_CONNECTED) {
                try {
                    bytesReadLength = btInputStream.read(buffer);
                    btHanlder.obtainMessage(Constants.MESSAGE_READ, bytesReadLength, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Message msg = btHanlder.obtainMessage(Constants.MESSAGE_TOAST);
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.TOAST, "Deverce connection is lost. ");
                    msg.setData(bundle);
                    btHanlder.sendMessage(msg);
                }

                if (btState != STATE_STOP) { //若連結失敗的話,就重新回到server mode
                    btState = STATE_NORMAL;
                    serverStart();
                }
                break;
            }
        }

        public void write(byte[] buffer) {
            try {
                btOutputStream.write(buffer);
            } catch (IOException e) {
            }
        }

        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
            }
        }
    }
        private class ConnectingThread extends  Thread{
            private final BluetoothSocket btSocket;
            private final BluetoothDevice btDevice;

            public ConnectingThread(BluetoothDevice device){
                btDevice=device;
                BluetoothSocket tmpSockect=null;
                try {
                    tmpSockect=device.createRfcommSocketToServiceRecord(UUID_String);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                btSocket=tmpSockect;
                btState=STATE_CONNECTED;
            }

            public void run(){
                try {
                    btSocket.connect();
                } catch (IOException e) {
                    try {
                        btSocket.close();
                    } catch (IOException e1) {
                    }
                    Message msg = btHanlder.obtainMessage(Constants.MESSAGE_TOAST);
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.TOAST,"Unable to connect device. ");
                    msg.setData(bundle);
                    btHanlder.sendMessage(msg);
                    if (btState!=STATE_NORMAL){
                        btState=STATE_NORMAL;
                        serverStart();
                    }
                    return;
                }

                if (btConnectedThread != null) {
                    btConnectedThread.cancel();
                    btConnectedThread = null;
                }
                if (btAcceptTread != null) {
                    btAcceptTread.cancel();
                    btAcceptTread = null;
                }
                btConnectedThread = new ConnectedThread(btSocket);
                btConnectedThread.start();

                Message msg = btHanlder.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.DEVICE_NAME, btDevice.getName());
                msg.setData(bundle);
                btHanlder.sendMessage(msg);
            }

            public void cancel() {
                try {
                    btSocket.close();
                } catch (IOException e) {
                }
            }

        }

}
