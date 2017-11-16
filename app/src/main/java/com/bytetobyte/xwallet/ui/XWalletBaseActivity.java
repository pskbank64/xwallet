package com.bytetobyte.xwallet.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.bytetobyte.xwallet.R;
import com.bytetobyte.xwallet.service.BlockchainService;
import com.bytetobyte.xwallet.service.MoneroBlockchainService;
import com.bytetobyte.xwallet.service.coin.CoinManagerFactory;
import com.bytetobyte.xwallet.service.ipcmodel.BlockDownloaded;
import com.bytetobyte.xwallet.service.ipcmodel.CoinTransaction;
import com.bytetobyte.xwallet.service.ipcmodel.MnemonicSeedBackup;
import com.bytetobyte.xwallet.service.ipcmodel.RecoverWalletMessage;
import com.bytetobyte.xwallet.service.ipcmodel.SpentValueMessage;
import com.bytetobyte.xwallet.service.ipcmodel.SyncedMessage;
import com.bytetobyte.xwallet.ui.lock.LockScreenActivity;
import com.bytetobyte.xwallet.util.EncryptUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by bruno on 22.03.17.
 */
public abstract class XWalletBaseActivity extends AppCompatActivity {

    // ACTIONS
    public static final String SEND_ACTION = "android.intent.action.SEND_COIN";
    public static final String ACTION_GENERATE_QR_CODE = "ACTION_GENERATE_QR_CODE";

    private Gson _gson;

    Messenger _bitcoinServiceMessenger = null;
    boolean _isBitcoinServiceBound;

    private ServiceConnection _bitcoinServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            _bitcoinServiceMessenger = new Messenger(service);
            _isBitcoinServiceBound = true;

            onServiceReady(CoinManagerFactory.BITCOIN);
            // notify fragments too
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            _bitcoinServiceMessenger = null;
            _isBitcoinServiceBound = false;
        }
    };

    Messenger _moneroServiceMessenger = null;
    boolean _isMoneroServiceBound;
    private ServiceConnection _moneroServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            _moneroServiceMessenger = new Messenger(service);
            _isMoneroServiceBound = true;

            onServiceReady(CoinManagerFactory.MONERO);
            // notify fragments too
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            _moneroServiceMessenger = null;
            _isMoneroServiceBound = false;
        }
    };

    /**
     *
     * @param msg
     */
    public void sendMessage(Message msg) {
        if (msg.arg1 == CoinManagerFactory.BITCOIN && !_isBitcoinServiceBound) return;

        if (msg.arg1 == CoinManagerFactory.MONERO && !_isMoneroServiceBound) return;
//        // Create and send a message to the service, using a supported 'what' value
//        Message msg = Message.obtain(null, BlockchainService.MSG_SAY_HELLO, 0, 0);
        try {
            msg.replyTo = new Messenger(new ResponseHandler());

            if (msg.arg1 == CoinManagerFactory.BITCOIN)
                _bitcoinServiceMessenger.send(msg);

            if (msg.arg1 == CoinManagerFactory.MONERO)
                _moneroServiceMessenger.send(msg);
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
        bindService(CoinManagerFactory.BITCOIN);
        bindService(CoinManagerFactory.MONERO);
    }

    /**
     *
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (_isBitcoinServiceBound) {
            unbindService(_bitcoinServiceConnection);
            _isBitcoinServiceBound = false;
        }

        if (_isMoneroServiceBound) {
            unbindService(_moneroServiceConnection);
            _isMoneroServiceBound = false;
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
    public void bindService(int coinId) {
        if (coinId == CoinManagerFactory.BITCOIN) {
            if (_isBitcoinServiceBound) return;
            bindService(new Intent(this, BlockchainService.class), _bitcoinServiceConnection, Context.BIND_AUTO_CREATE);
        }

        if (coinId == CoinManagerFactory.MONERO) {
            if (_isMoneroServiceBound) return;
            bindService(new Intent(this, MoneroBlockchainService.class), _moneroServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public boolean isBitcoinServiceBound() {
        return _isBitcoinServiceBound;
    }

    public boolean isMoneroServiceBound() {
        return _isMoneroServiceBound;
    }

    /**
     *
     * @param address
     * @param amount
     */
    public void sendCoins(String address, String amount, String txFee, Map<Integer, Object> extraOptions, int coinId) {
        SpentValueMessage spentToAmount = new SpentValueMessage(address, amount, txFee, extraOptions);

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
    public void requestTxFee(String address, String amount, Map<Integer, Object> extraOptions, int coinId) {
        SpentValueMessage spentToAmount = new SpentValueMessage(address, amount, null, extraOptions);

        Gson gson = new Gson();
        String spentToAmountJson = gson.toJson(spentToAmount);

        Message spentMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_CALCULATE_FEE, coinId, 0);
        spentMsg.getData().putString(BlockchainService.IPC_BUNDLE_DATA_KEY, spentToAmountJson);
        sendMessage(spentMsg);
    }

    /**
     *
     * @param coinId
     * @param seed
     * @param creationDate
     */
    public void recoverWallet(int coinId, String seed, Date creationDate, long blockheight) {
        System.out.println("recoverWallet coinId : " + coinId);

        RecoverWalletMessage recoverWalletMessage = new RecoverWalletMessage(seed, creationDate, blockheight);

        Gson gson = new Gson();
        String jsonStr = gson.toJson(recoverWalletMessage);

        Message recoverMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_RECOVER, coinId, 0);
        recoverMsg.getData().putString(BlockchainService.IPC_BUNDLE_DATA_KEY, jsonStr);
        sendMessage(recoverMsg);
    }

    /**
     *
     */
    public void syncChain(int coinId) {
        System.out.println("syncChain : coinId " + coinId);
        //if (CoinManagerFactory.MONERO == coinId) return; // skip

        Message sendMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_SYNC, coinId, 0);

        Bundle data = new Bundle();
        data.putString("key", EncryptUtils.KSEED);
        sendMsg.obj = data;
        sendMessage(sendMsg);
    }

    /**
     *
     * @param coinId
     */
    public void stopChain(int coinId) {
        System.out.println("Stopping chain");

        Message stopMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_CLOSE, coinId, 0);
        sendMessage(stopMsg);
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
     * @param coinId
     */
    public void requestMnemonic(int coinId) {
        Message sendMsg = Message.obtain(null, BlockchainService.IPC_MSG_WALLET_MNENOMIC_SEED, coinId, 0);
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

            System.out.println("XWalletBaseActivity#ResponseHandler::handleMessage msg : " + respCode);

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

                case BlockchainService.IPC_MSG_WALLET_MNENOMIC_SEED:
                    bundledData = msg.getData().getString(BlockchainService.IPC_BUNDLE_DATA_KEY);
                    MnemonicSeedBackup seedBackup = _gson.fromJson(bundledData, MnemonicSeedBackup.class);
                    onMnemonicSeedBackup(seedBackup);
                    break;

                default:
                    break;
            }
        }

    }

    protected void onServiceReady(int coinId) {
       // syncChain(CoinManagerFactory.BITCOIN);
       // syncChain(CoinManagerFactory.MONERO);
    }

    protected void onSyncReady(SyncedMessage syncedMessage) {}
    protected void onBlockDownloaded(BlockDownloaded block) {}
    protected void onFeeCalculated(SpentValueMessage feeSpentcal) {}
    protected void onTransactions(List<CoinTransaction> txs) {}
    protected void onMnemonicSeedBackup(MnemonicSeedBackup seedBackup) {}
    protected void onLockPinResult(int requestCode, int resultCode) {};

    // News
    public String getNewsAuthToken() {
        return null;
    }


    /**
     *
     */
    public void toLock(int requestCode) {
        SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);
        String pinSet = prefs.getString(LockScreenActivity.PREFS_KEY_PIN, null);

        String lockAction = LockScreenActivity.UNLOCK_PIN_ACTION;
        if (pinSet == null) {
            lockAction = LockScreenActivity.SET_PIN_ACTION;
        }

        Intent intent = new Intent(this, LockScreenActivity.class);
        intent.setAction(lockAction);
        startActivityForResult(intent, requestCode);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    /**
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        onLockPinResult(requestCode, resultCode);
    }
}