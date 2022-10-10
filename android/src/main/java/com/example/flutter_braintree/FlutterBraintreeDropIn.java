package com.example.flutter_braintree;

import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.braintreepayments.api.DropInListener;
import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalCheckoutRequest;


import com.braintreepayments.api.ThreeDSecurePostalAddress;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.braintreepayments.api.UserCanceledException;
import com.google.android.gms.wallet.TransactionInfo;

import java.util.HashMap;

public class FlutterBraintreeDropIn implements FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener {
  private static final String TAG = "FlutterBraintreeDropIn";

  private FragmentActivity activity;
  private Result activeResult;
  private MethodChannel methodChannel;

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    Log.i(TAG, "onAttachedToEngine");

    methodChannel = new MethodChannel(binding.getBinaryMessenger(), "flutter_braintree.drop_in");
    methodChannel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {
    Log.i(TAG, "onDetachedFromEngine");

    methodChannel.setMethodCallHandler(null);
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    Log.i(TAG, "onAttachedToActivity");

    if(binding.getActivity() instanceof FragmentActivity) {
      Log.i(TAG, "onAttachedToActivity getActivity instanceof FragmentActivity");
      activity = (FragmentActivity) binding.getActivity();
    } else {
      Log.e(TAG, "onAttachedToActivity nije instanceof FragmentActivity");
    }
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    Log.i(TAG, "onDetachedFromActivityForConfigChanges");
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    Log.i(TAG, "onReattachedToActivityForConfigChanges");
  }

  @Override
  public void onDetachedFromActivity() {
    Log.i(TAG, "onDetachedFromActivity");
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("start")) {
      String clientToken = call.argument("clientToken");
      String tokenizationKey = call.argument("tokenizationKey");

      DropInRequest dropInRequest = new DropInRequest();
      dropInRequest.setMaskCardNumber((Boolean) call.argument("maskCardNumber"));
      dropInRequest.setVaultManagerEnabled((Boolean) call.argument("vaultManagerEnabled"));

      if (call.hasArgument("amount")) {
        ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
        threeDSecureRequest.setVersionRequested(ThreeDSecureRequest.VERSION_2);

        String amount = call.argument("amount");
        threeDSecureRequest.setAmount(amount);

        String email = call.argument("email");
        if (email != null) {
          threeDSecureRequest.setEmail(email);
        }

        ThreeDSecurePostalAddress billingAddress = readBillingAddress(call);
        if (billingAddress != null) {
          threeDSecureRequest.setBillingAddress(readBillingAddress(call));
        }

        dropInRequest.setThreeDSecureRequest(threeDSecureRequest);
      }

      if (call.hasArgument("cardholderNameSetting"))
        dropInRequest.setCardholderNameStatus((Integer) call.argument("cardholderNameSetting"));

      readGooglePaymentParameters(dropInRequest, call);
      readPayPalParameters(dropInRequest, call);
      if (!((Boolean) call.argument("venmoEnabled")))
        dropInRequest.setVenmoDisabled(true);
      if (!((Boolean) call.argument("cardEnabled")))
        dropInRequest.setCardDisabled(true);
      if (!((Boolean) call.argument("paypalEnabled")))
        dropInRequest.setPayPalDisabled(true);

      if (activeResult != null) {
        result.error("drop_in_already_running", "Cannot launch another Drop-in activity while one is already running.", null);
        return;
      }
      this.activeResult = result;

      BraintreeDropInFragment braintreeFragment = new BraintreeDropInFragment(dropInRequest, clientToken != null ? clientToken : tokenizationKey, new DropInListener() {
        @Override
        public void onDropInSuccess(@NonNull DropInResult dropInResult) {
          Log.i(TAG, "onDropInSuccess");


          HashMap<String, Object> nonceResult = new HashMap<String, Object>();
          nonceResult.put("nonce", dropInResult.getPaymentMethodNonce().getString());
          nonceResult.put("typeLabel", activity.getString(dropInResult.getPaymentMethodType().getLocalizedName()));
          nonceResult.put("description", dropInResult.getPaymentDescription());
          nonceResult.put("isDefault", dropInResult.getPaymentMethodNonce().isDefault());

          HashMap<String, Object> result = new HashMap<String, Object>();
          result.put("paymentMethodNonce", nonceResult);
          result.put("deviceData", dropInResult.getDeviceData());

          activeResult.success(result);
          activeResult = null;
        }

        @Override
        public void onDropInFailure(@NonNull Exception error) {
          Log.e(TAG, "onDropInFailure, fail: " + error.toString());

          if (error instanceof UserCanceledException) {
            activeResult.success(null);
          } else {
            activeResult.error("braintree_error", error.getMessage(), null);
          }

          activeResult = null;
        }
      });

      FragmentManager fm = activity.getSupportFragmentManager();
      fm.beginTransaction()
              .replace(android.R.id.content, (Fragment) braintreeFragment)
              .commitNow();

      braintreeFragment.launch();
    } else {
      result.notImplemented();
    }
  }

  private static ThreeDSecurePostalAddress readBillingAddress(MethodCall call) {
    HashMap<String, String> arg = call.argument("billingAddress");
    if (arg == null) {
      return null;
    }

    ThreeDSecurePostalAddress postalAddress = new ThreeDSecurePostalAddress();

    if (call.hasArgument("givenName"))
      postalAddress.setGivenName((String) call.argument("givenName"));
    if (call.hasArgument("surname"))
      postalAddress.setSurname((String) call.argument("surname"));
    if (call.hasArgument("phoneNumber"))
      postalAddress.setPhoneNumber((String) call.argument("phoneNumber"));
    if (call.hasArgument("streetAddress"))
      postalAddress.setStreetAddress((String) call.argument("streetAddress"));
    if (call.hasArgument("extendedAddress"))
      postalAddress.setExtendedAddress((String) call.argument("extendedAddress"));
    if (call.hasArgument("locality"))
      postalAddress.setLocality((String) call.argument("locality"));
    if (call.hasArgument("region"))
      postalAddress.setRegion((String) call.argument("region"));
    if (call.hasArgument("postalCode"))
      postalAddress.setPostalCode((String) call.argument("postalCode"));
    if (call.hasArgument("countryCodeAlpha2"))
      postalAddress.setCountryCodeAlpha2((String) call.argument("countryCodeAlpha2"));

    return postalAddress;
  }

  private static void readGooglePaymentParameters(DropInRequest dropInRequest, MethodCall call) {
    HashMap<String, Object> arg = call.argument("googlePaymentRequest");
    if (arg == null) {
      dropInRequest.setGooglePayDisabled(true);
      return;
    }

    TransactionInfo.Builder transactionInfoBuilder = TransactionInfo.newBuilder()
            .setTotalPriceStatus((int) arg.get("priceStatus"))
            .setCurrencyCode((String) arg.get("currencyCode"));

    if (arg.containsKey("totalPrice")) {
      transactionInfoBuilder.setTotalPrice((String) arg.get("totalPrice"));
    }

    GooglePayRequest googlePaymentRequest = new GooglePayRequest();
    googlePaymentRequest.setTransactionInfo(transactionInfoBuilder.build());
    googlePaymentRequest.setBillingAddressRequired((Boolean) arg.get("billingAddressRequired"));
    googlePaymentRequest.setGoogleMerchantName((String) arg.get("merchantID"));
    dropInRequest.setGooglePayRequest(googlePaymentRequest);
  }

  private static void readPayPalParameters(DropInRequest dropInRequest, MethodCall call) {
    HashMap<String, Object> arg = call.argument("paypalRequest");
    if (arg == null) {
      dropInRequest.setPayPalDisabled(true);
      return;
    }
    String amount = (String) arg.get("amount");
    PayPalCheckoutRequest paypalRequest = new PayPalCheckoutRequest(amount);
    paypalRequest.setCurrencyCode((String) arg.get("currencyCode"));
    paypalRequest.setDisplayName((String) arg.get("displayName"));
    paypalRequest.setBillingAgreementDescription((String) arg.get("billingAgreementDescription"));
    dropInRequest.setPayPalRequest(paypalRequest);
  }


  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data)  {
    if (activeResult == null)
      return false;

    return true;
  }
}