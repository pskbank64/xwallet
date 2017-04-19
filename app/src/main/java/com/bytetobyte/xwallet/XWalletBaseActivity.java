package com.bytetobyte.xwallet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.bytetobyte.xwallet.service.BlockchainService;
import com.bytetobyte.xwallet.service.ipcmodel.BlockDownloaded;
import com.bytetobyte.xwallet.service.ipcmodel.CoinTransaction;
import com.bytetobyte.xwallet.service.ipcmodel.SpentValueMessage;
import com.bytetobyte.xwallet.service.ipcmodel.SyncedMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bruno on 22.03.17.
 */
public abstract class XWalletBaseActivity extends AppCompatActivity {

    private Gson _gson;

    protected abstract void onServiceReady();
    protected abstract void onSyncReady(SyncedMessage syncedMessage);
    protected abstract void onBlockDownloaded(BlockDownloaded block);
    protected abstract void onFeeCalculated(SpentValueMessage feeSpentcal);
    protected abstract void onTransactions(List<CoinTransaction> txs);

    // News
    public abstract String getNewsAuthToken();

    /**
     *
     * @param msg
     */
    public void sendMessage(Message msg) {
        if (!mBound) return;
//        // Create and send a message to the service, using a supported 'what' value
//        Message msg = Message.obtain(null, BlockchainService.MSG_SAY_HELLO, 0, 0);
        try {
            msg.replyTo = new Messenger(new ResponseHandler());

            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     */
    @Override
    protected void onStart() {
        super.onStart();
        bind();
    }

    /**
     *
     */
    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    /**
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        if (_gson == null) {
            _gson = new Gson();
        }
    }

    /**
     *
     * @param intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    /**
     *
     */
    public void bind() {
        if (mBound) return;

        bindService(new Intent(this, BlockchainService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    public boolean getIsBound() {
        return mBound;
    }

    /**
     *
     * @param address
     * @param amount
     */
    public void sendCoins(String address, String amount, int coinId) {
        SpentValueMessage spentToAmount = new SpentValueMessage(address, amount);

        Gson gson = new Gson();
        String spentToAmountJson = gson.toJson(spentToAmount);

        Message spentMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_SEND_AMOUNT, coinId, 0);
        spentMsg.getData().putString(BlockchainService.IPC_BUNDLE_DATA_KEY, spentToAmountJson);
        sendMessage(spentMsg);
    }

    /**
     *
     * @param address
     * @param amount
     */
    public void requestTxFee(String address, String amount, int coinId) {
        SpentValueMessage spentToAmount = new SpentValueMessage(address, amount);

        Gson gson = new Gson();
        String spentToAmountJson = gson.toJson(spentToAmount);

        Message spentMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_CALCULATE_FEE, coinId, 0);
        spentMsg.getData().putString(BlockchainService.IPC_BUNDLE_DATA_KEY, spentToAmountJson);
        sendMessage(spentMsg);
    }

    /**
     *
     */
    public void syncChain(int coinId) {
        Message sendMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_SYNC, coinId, 0);
        sendMessage(sendMsg);
    }

    /**
     *
     * @param coinId
     */
    public void requestTxList(int coinId) {
        Message sendMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_TRANSACTION_LIST, coinId, 0);
        sendMessage(sendMsg);
    }

    /**
     *
     */
    // This class handles the Service response
    class ResponseHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            int respCode = msg.what;
            String bundledData = null;

            //System.out.println("XWalletBaseActivity#ResponseHandler::handleMessage msg : " + respCode);

            switch (respCode) {

                case BlockchainService.IPC_MSG_WALLET_SYNC:
                    bundledData = msg.getData().getString(BlockchainService.IPC_BUNDLE_DATA_KEY);
                    SyncedMessage syncedMessage = _gson.fromJson(bundledData, SyncedMessage.class);
                    onSyncReady(syncedMessage);
                    break;

                case BlockchainService.IPC_MSG_WALLET_BLOCK_DOWNLOADED:
                    bundledData = msg.getData().getString(BlockchainService.IPC_BUNDLE_DATA_KEY);
                    BlockDownloaded blocksDownloadMsg = _gson.fromJson(bundledData, BlockDownloaded.class);
                    onBlockDownloaded(blocksDownloadMsg);
                    break;

                case BlockchainService.IPC_MSG_WALLET_CALCULATE_FEE:
                    bundledData = msg.getData().getString(BlockchainService.IPC_BUNDLE_DATA_KEY);
                    SpentValueMessage feeSpentcal = _gson.fromJson(bundledData, SpentValueMessage.class);
                    onFeeCalculated(feeSpentcal);
                    break;

                case BlockchainService.IPC_MSG_WALLET_TRANSACTION_LIST:
                    bundledData = msg.getData().getString(BlockchainService.IPC_BUNDLE_DATA_KEY);
                    Type listType = new TypeToken<ArrayList<CoinTransaction>>(){}.getType();
                    List<CoinTransaction> txs = _gson.fromJson(bundledData, listType);
                    onTransactions(txs);
                    break;

                default:
                    break;
            }
        }

    }

    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mService = new Messenger(service);
            mBound = true;

            onServiceReady();
            // notify fragments too
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mBound = false;
        }
    };

}
