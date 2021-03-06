package com.bytetobyte.xwallet.service.coin.bitcoin.actions;

import com.bytetobyte.xwallet.service.coin.CoinAction;
import com.bytetobyte.xwallet.service.coin.CurrencyCoin;
import com.bytetobyte.xwallet.service.coin.bitcoin.Bitcoin;
import com.bytetobyte.xwallet.service.coin.bitcoin.BitcoinManager;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;

/**
 * Created by bruno on 25.03.17.
 */
public class BitcoinSendAction implements CoinAction<CoinAction.CoinActionCallback<CurrencyCoin>>, WalletCoinsSentEventListener {

    //
    private final Bitcoin _bitcoin;
    private final String _address;
    private final String _amount;

    //
    private CoinActionCallback<CurrencyCoin>[] _callbacks;

    /**
     *
     * @param address
     * @param amount
     * @param currencyCoin
     */
    public BitcoinSendAction(String address, String amount, CurrencyCoin<WalletAppKit> currencyCoin) {
        this._address = address;
        this._amount = amount;
        this._bitcoin = (Bitcoin) currencyCoin;
    }

    /**
     *
     * @param callbacks
     */
    @Override
    public void execute(final CoinActionCallback<CurrencyCoin>... callbacks) {
        this._callbacks = callbacks;
        this._bitcoin.getWalletManager().wallet().addCoinsSentEventListener(this);

        Coin balance = _bitcoin.getWalletManager().wallet().getBalance();
        Coin amountCoin = Coin.parseCoin(_amount);

        Address addr = Address.fromBase58(_bitcoin.getWalletManager().wallet().getParams(), _address);
        SendRequest sendRequest = SendRequest.to(addr, amountCoin);

        Coin feeCoin = BitcoinManager.CalculateFeeTxSizeBytes(sendRequest.tx, sendRequest.feePerKb.getValue());

        long balValue = balance.getValue();
        long amountValue = amountCoin.getValue();
        long txFeeValue = feeCoin.getValue();

        if (amountValue + txFeeValue > balValue) {
            amountCoin = Coin.valueOf(balValue - txFeeValue);
            sendRequest = SendRequest.to(addr, amountCoin);
        }

        try {
            _bitcoin.getWalletManager().wallet().sendCoins(sendRequest);
        } catch (InsufficientMoneyException e) {

            for (CoinActionCallback<CurrencyCoin> callback : _callbacks) {
                callback.onError(_bitcoin);
            }
            e.printStackTrace();
        }
    }

    /**
     *
     * @param wallet
     * @param transaction
     * @param coin
     * @param coin1
     */
    @Override
    public void onCoinsSent(Wallet wallet, Transaction transaction, Coin coin, Coin coin1) {
        for (CoinActionCallback<CurrencyCoin> callback : _callbacks) {
            callback.onResult(_bitcoin);
        }
    }
}
