package com.mobile.USSD;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.klinker.android.send_message.Message;
import com.klinker.android.send_message.Settings;
import com.klinker.android.send_message.Transaction;
import com.romellfudi.ussdlibrary.USSDApi;
import com.romellfudi.ussdlibrary.USSDController;
import com.tuenti.smsradar.Sms;
import com.tuenti.smsradar.SmsListener;
import com.tuenti.smsradar.SmsRadar;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class TelephonyAssistantModule extends ReactContextBaseJavaModule {

    private final TelephonyManager[] SIMManagers = new TelephonyManager[3];
    private final HashMap map = new HashMap<>();
    private int SIMCount = 0;
    private final ReactApplicationContext context;
    private Boolean executing = false;

    @SuppressLint("MissingPermission")
    USSD(ReactApplicationContext context) {
        super(context);
        this.context = context;
        map.put("KEY_LOGIN", new HashSet<>(Arrays.asList("espere", "waiting", "loading", "esperando")));
        map.put("KEY_ERROR", new HashSet<>(Arrays.asList("problema", "problem", "error", "null")));

    }

    @Override
    @NonNull
    public String getName() {
        return "USSD";
    }
    @SuppressLint("MissingPermission")
    @ReactMethod
    public void verifySIM(Promise promise) {
        getSIMCount();
        SubscriptionManager subscriptionManager = (SubscriptionManager) context
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        for (int i=0; i<SIMCount;i++) {
            if (subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(i) == null) promise.resolve(true);
            else promise.resolve(false);
        }
    }
    private void getSIMCount() {

            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            @SuppressLint("MissingPermission") List<PhoneAccountHandle> phoneAccountHandleList = telecomManager.getCallCapablePhoneAccounts();
            SIMCount = phoneAccountHandleList.size();
    }
    @ReactMethod
    public void getSIMCount(Promise promise) {
        if (SIMCount == 0) getSIMCount();
        promise.resolve(SIMCount);
    }

    @ReactMethod
    public void isExecuting(Promise promise) {
        promise.resolve(executing);
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void getCarrier(int sim, Promise promise) {
        if (sim >= SIMCount) throw new Error("Invalid SIM ID entered");
        if (SIMManagers[0] == null) {
            SubscriptionManager subscriptionManager = (SubscriptionManager) context
                    .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            for (int i = 0; i < SIMCount; i++) {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                SIMManagers[i] = telephonyManager.createForSubscriptionId(
                        subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(i).getSubscriptionId());
            }
        }
        promise.resolve(SIMManagers[sim].getSimOperatorName());
    }

    @ReactMethod
    public void executeUSSD(String ussd, int sim, boolean confirm , Promise promise) {
        USSDController ussdApi = USSDController.getInstance(context);
        ussdApi.verifyAccesibilityAccess(context.getCurrentActivity());
        ussdApi.verifyOverLay(context.getCurrentActivity());
        if (executing)
            promise.reject(new Error("A USSD code is already being executed"));
        executing = true;
        ussdApi.callUSSDInvoke(ussd, sim, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                // message has the response string data
                if (confirm) {
                    ussdApi.send("1", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            // message has the response string data from USSD
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                            executing = false;
                            ussdApi.cancel();
                            promise.resolve(message);
                        }
                    });
                }
                else {
                    ussdApi.cancel();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {

                    }
                    executing = false;
                    promise.resolve(message);
                }




            }

            @Override
            public void over(String message) {
                // message has the response string data from USSD or error
                // response no have input text, NOT SEND ANY DATA
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
                executing = false;
                promise.resolve(message);
            }
        });
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void sendSMS(String message, String destination, int sim, Promise promise) {
        Settings sendSettings = new Settings();
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        sendSettings.setSubscriptionId(subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(sim).getSubscriptionId());
        Transaction sendTransaction = new Transaction(context, sendSettings);
        Message mMessage = new Message(message, destination);
        SmsRadar.initializeSmsRadarService(context, new SmsListener() {

            @Override
            public void onSmsSent(Sms sms) {

            }

            @Override
            public void onSmsReceived(Sms sms) {
                promise.resolve(sms.getMsg());
            }
        });
        sendTransaction.sendNewMessage(mMessage, Transaction.NO_THREAD_ID);
    }

}