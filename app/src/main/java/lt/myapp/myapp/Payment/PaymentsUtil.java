package lt.myapp.myapp.Payment;
/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.app.AlertDialog;
import lt.myapp.myapp.sys.TimeToolsExtra.SNTPClient;

public class PaymentsUtil {
    /**
     * Arbitrarily-picked constant integer you define to track a request for payment data activity.
     *
     * @value #LOAD_PAYMENT_DATA_REQUEST_CODE
     */
    public static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;//default
    private static final BigDecimal MICROS = new BigDecimal(1000000d);

    /**
     * our listener
     */
    public interface PUListener {
        void onSuccess(JSONObject paymentMethodData);
    }

    /**
     * A client for interacting with the Google Pay API.
     *
     * @see <a
     * href="https://developers.google.com/android/reference/com/google/android/gms/wallet/PaymentsClient">PaymentsClient</a>
     */
    private PaymentsClient paymentsClient = null;

    /**
     * Constructor with payments client initialization
     *
     * @param activity
     */
    public PaymentsUtil(Activity activity) {
        paymentsClient = createPaymentsClient(activity);
    }

    /**
     * 1. defining api version
     * Create a Google Pay API base request object with properties used in all requests.
     *
     * @return Google Pay API base request object.
     * @throws JSONException
     */
    private static JSONObject getBaseRequest() throws JSONException {
        return new JSONObject().put("apiVersion", 2).put("apiVersionMinor", 0);
    }

    /**
     * 2. Choosing a payment tokenization method: GATEWAY
     * <p>
     * Gateway Integration: Identify your gateway and your app's gateway merchant identifier.
     *
     * <p>The Google Pay API response will return an encrypted payment method capable of being charged
     * by a supported gateway after payer authorization.
     *
     * @return Payment data tokenization for the CARD payment method.
     * @throws JSONException
     * @see <a href=
     * "https://developers.google.com/pay/api/android/reference/object#PaymentMethodTokenizationSpecification">PaymentMethodTokenizationSpecification</a>
     */
    private static JSONObject getGatewayTokenizationSpecification() throws JSONException {
        return new JSONObject() {{
               /*
            For CARD payment method, use PAYMENT_GATEWAY or DIRECT. For PAYPAL PaymentMethod, use DIRECT with no parameter.
            https://developers.google.com/pay/api/android/reference/request-objects#gateway
             */
            put("type", "PAYMENT_GATEWAY");
            put("parameters", new JSONObject() {
                {
                    put("gateway", "example");
                    put("gatewayMerchantId", "exampleGatewayMerchantId");
                }
            });
        }};
    }


    /**
     * 3. Define supported payment card networks
     * <p>
     * Card networks supported by your app and your gateway.
     *
     * <p>TODO: Confirm card networks supported by your app and gateway & update in Constants.java.
     *
     * @return Allowed card networks
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#CardParameters">CardParameters</a>
     */
    private static JSONArray getAllowedCardNetworks() {
        //return new JSONArray(Constants.SUPPORTED_NETWORKS); //move to constants? final String[] = new String[]{"VISA", "AMEX"}...?
        return new JSONArray()
                .put("AMEX")
                .put("DISCOVER")
                .put("INTERAC")
                .put("JCB")
                .put("MASTERCARD")
                .put("VISA");
    }

    /**
     * The Google Pay API may return cards on file on Google.com (PAN_ONLY) and/or a device token on an Android device authenticated with a 3-D Secure cryptogram (CRYPTOGRAM_3DS).
     * <p>
     * Card authentication methods supported by your app and your gateway.
     *
     * <p>TODO: Confirm your processor supports Android device tokens on your supported card networks
     * and make updates in Constants.java.
     *
     * @return Allowed card authentication methods.
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#CardParameters">CardParameters</a>
     */
    private static JSONArray getAllowedCardAuthMethods() {
        //return new JSONArray(Constants.SUPPORTED_METHODS);
        return new JSONArray()
                .put("PAN_ONLY")
                .put("CRYPTOGRAM_3DS");
    }

    /**
     * 4. Describe your allowed payment methods
     * <p>
     * Describe your app's support for the CARD payment method.
     *
     * <p>The provided properties are applicable to both an IsReadyToPayRequest and a
     * PaymentDataRequest.
     *
     * @return A CARD PaymentMethod object describing accepted cards.
     * @throws JSONException
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#PaymentMethod">PaymentMethod</a>
     */
    private static JSONObject getBaseCardPaymentMethod() throws JSONException {
        JSONObject cardPaymentMethod = new JSONObject();
        cardPaymentMethod.put("type", "CARD");

        JSONObject parameters = new JSONObject();
        parameters.put("allowedAuthMethods", getAllowedCardAuthMethods());
        parameters.put("allowedCardNetworks", getAllowedCardNetworks());
        // Optionally, you can add billing address/phone number associated with a CARD payment method.
        parameters.put("billingAddressRequired", true);

        JSONObject billingAddressParameters = new JSONObject();
        billingAddressParameters.put("format", "FULL");

        parameters.put("billingAddressParameters", billingAddressParameters);

        cardPaymentMethod.put("parameters", parameters);

        return cardPaymentMethod;
    }

    /**
     * 5. Create payments client
     * <p>
     * Creates an instance of {@link PaymentsClient} for use in an {@link Activity} using the
     * environment and theme set in {@link WalletConstants}.
     *
     * @param activity
     */
    public static PaymentsClient createPaymentsClient(Activity activity) {
        Wallet.WalletOptions walletOptions = new Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_TEST) //WalletConstants.ENVIRONMENT_TEST || WalletConstants.ENVIRONMENT_PRODUCTION
                .build();
        return Wallet.getPaymentsClient(activity, walletOptions);
    }

    /**
     * Describe the expected returned payment data for the CARD payment method
     *
     * @return A CARD PaymentMethod describing accepted cards and optional fields.
     * @throws JSONException
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#PaymentMethod">PaymentMethod</a>
     */
    private static JSONObject getCardPaymentMethod() throws JSONException {
        JSONObject cardPaymentMethod = getBaseCardPaymentMethod();
        cardPaymentMethod.put("tokenizationSpecification", getGatewayTokenizationSpecification());

        return cardPaymentMethod;
    }

    /**
     * 6. Determine readiness to pay with the Google Pay API, part "getIsReadyToPayRequest"
     * <p>
     * An object describing accepted forms of payment by your app, used to determine a viewer's
     * readiness to pay.
     *
     * @return API version and payment methods supported by the app.
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#IsReadyToPayRequest">IsReadyToPayRequest</a>
     */
    public static /*Optional<*/JSONObject/*>*/ getIsReadyToPayRequest() {
        try {
            JSONObject isReadyToPayRequest = getBaseRequest();
            isReadyToPayRequest.put(
                    "allowedPaymentMethods", new JSONArray().put(getBaseCardPaymentMethod()));

            return isReadyToPayRequest; //Optional.of(isReadyToPayRequest); // API 24
        } catch (JSONException e) {
            return null; //Optional.empty(); //API 24
        }
    }


    /**
     * 6. Determine readiness to pay with the Google Pay API
     * display the Google Pay button, call the isReadyToPay API to determine if the user can make payments with the Google Pay API.
     */
    public void possiblyShowGooglePayButton(OnCompleteListener<Boolean> onCompleteListener) {
        final JSONObject isReadyToPayJson = getIsReadyToPayRequest();
        if (isReadyToPayJson == null) {
            return;
        }
        IsReadyToPayRequest request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString());
        if (request == null) {
            return;
        }

        // The call to isReadyToPay is asynchronous and returns a Task. We need to provide an
        // OnCompleteListener to be triggered when the result of the call is known.
        Task<Boolean> task = paymentsClient.isReadyToPay(request);
        task.addOnCompleteListener(onCompleteListener);
    }

    /**
     * Provide Google Pay API with a payment amount, currency, and amount status.
     * Important: European Economic Area (EEA) merchants must pass in countryCode, totalPrice, totalPriceStatus and merchantName parameters to meet SCA requirements.
     *
     * @return information about the requested payment.
     * @throws JSONException
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#TransactionInfo">TransactionInfo</a>
     */
    private static JSONObject getTransactionInfo(String price) throws JSONException {
        JSONObject transactionInfo = new JSONObject();
        transactionInfo.put("totalPrice", price);
        transactionInfo.put("totalPriceStatus", "FINAL");
        transactionInfo.put("countryCode", "LT");
        transactionInfo.put("currencyCode", "EUR");

        return transactionInfo;
    }

    /**
     * Information about the merchant requesting payment information
     * Provide a user-visible merchant name
     *
     * @return Information about the merchant.
     * @throws JSONException
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#MerchantInfo">MerchantInfo</a>
     */
    private static JSONObject getMerchantInfo() throws JSONException {
        return new JSONObject().put("merchantName", "Example Merchant");
    }

    /**
     * 7.Create a PaymentDataRequest object
     * An object describing information requested in a Google Pay payment sheet
     *
     * @return Payment data expected by your app.
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#PaymentDataRequest">PaymentDataRequest</a>
     */
    public static /*Optional<*/JSONObject/*>*/ getPaymentDataRequest(String price) {
        try {
            JSONObject paymentDataRequest = getBaseRequest();
            paymentDataRequest.put("allowedPaymentMethods", new JSONArray().put(getCardPaymentMethod()));
            paymentDataRequest.put("transactionInfo", getTransactionInfo(price));
            paymentDataRequest.put("merchantInfo", getMerchantInfo());

            /* An optional shipping address requirement is a top-level property of the PaymentDataRequest
            JSON object. */
            /*paymentDataRequest.put("shippingAddressRequired", true);

            JSONObject shippingAddressParameters = new JSONObject();
            shippingAddressParameters.put("phoneNumberRequired", false);

            JSONArray allowedCountryCodes = new JSONArray(Constants.SHIPPING_SUPPORTED_COUNTRIES);

            shippingAddressParameters.put("allowedCountryCodes", allowedCountryCodes);
            paymentDataRequest.put("shippingAddressParameters", shippingAddressParameters);*/
            return /*Optional.of(*/paymentDataRequest/*)*/;
        } catch (JSONException e) {
            return /*Optional.empty()*/ null;
        }
    }

    /**
     * Converts micros to a string format accepted by {@link PaymentsUtil#getPaymentDataRequest}.
     *
     * @param micros value of the price.
     */
    public static String microsToString(long micros) {
        return microsToBigDecimal(micros).toString();
    }

    /**
     * Converts micros to a big decimal format
     *
     * @param micros
     * @return
     */
    public static BigDecimal microsToBigDecimal(long micros) {
        return new BigDecimal(micros).divide(MICROS).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * This method is called when the Pay with Google button is clicked.
     * Uses defaut request code
     *
     * @param view
     * @param activity
     * @param microsPrice //@param mShippingCost
     */
    public void requestPayment(View view, Activity activity, long microsPrice/*, long mShippingCost*/) {
        requestPayment(view, activity, microsPrice, LOAD_PAYMENT_DATA_REQUEST_CODE);
    }

    /**
     * This method is called when the Pay with Google button is clicked.
     * Can set custom request code
     *
     * @param view
     * @param activity
     * @param microsPrice //@param mShippingCost
     * @param requestCode custom request code
     */
    public void requestPayment(View view, Activity activity, long microsPrice, int requestCode/*, long mShippingCost*/) {
        // Disables the button to prevent multiple clicks.
        view.setClickable(false);

        // The price provided to the API should include taxes and shipping.
        // This price is not displayed to the user.
        String price = microsToString(microsPrice/* + mShippingCost*/);

        // TransactionInfo transaction = PaymentsUtil.createTransaction(price);
        /*Optional<*/
        JSONObject/*>*/ paymentDataRequestJson = getPaymentDataRequest(price); //Optional is for api v24
        if (paymentDataRequestJson == null/*!paymentDataRequestJson.isPresent()*/) {
            return;
        }
        PaymentDataRequest request = PaymentDataRequest.fromJson(paymentDataRequestJson/*.get()*/.toString());

        // Since loadPaymentData may show the UI asking the user to select a payment method, we use
        // AutoResolveHelper to wait for the user interacting with it. Once completed,
        // onActivityResult will be called with the result.
        if (request != null) {
            AutoResolveHelper.resolveTask(paymentsClient.loadPaymentData(request), activity, requestCode);
        }
    }


    /**
     * Activity should handle payment activity result by passing it to this function in case of request code being equal to LOAD_PAYMENT_DATA_REQUEST_CODE
     * <p>
     * //@param requestCode
     *
     * @param resultCode
     * @param data
     * @param mGooglePayButton
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data, View mGooglePayButton, SNTPClient.Listener sntpListener, PUListener puListener) {
        //switch (requestCode) {
        // value passed in AutoResolveHelper
        //case LOAD_PAYMENT_DATA_REQUEST_CODE:
        switch (resultCode) {
            case Activity.RESULT_OK:
                PaymentData paymentData = PaymentData.getFromIntent(data);
                handlePaymentSuccess(requestCode, paymentData, mGooglePayButton.getContext(), sntpListener, puListener);
                break;
            case Activity.RESULT_CANCELED:
                // Nothing to here normally - the user simply cancelled without selecting a
                // payment method.
                break;
            case AutoResolveHelper.RESULT_ERROR:
                Status status = AutoResolveHelper.getStatusFromIntent(data);
                handleError(status.getStatusCode());
                break;
            default:
                // Do nothing.
        }

        // Re-enables the Google Pay payment button.
        mGooglePayButton.setClickable(true);
        //System.out.println("onActivityResult: enabling back the mGooglePayButton=" + mGooglePayButton);
        // break;
        //}
    }

    /**
     * PaymentData response object contains the payment information, as well as any additional
     * requested information, such as billing and shipping address.
     *
     * @param paymentData A response object returned by Google after a payer approves payment.
     * @see <a
     * href="https://developers.google.com/pay/api/android/reference/object#PaymentData">Payment
     * Data</a>
     */
    private void handlePaymentSuccess(int requestCode, PaymentData paymentData, Context context, SNTPClient.Listener sntpListener, PUListener puListener) {
        String paymentInformation = paymentData.toJson();

        // Token will be null if PaymentDataRequest was not constructed using fromJson(String).
        if (paymentInformation == null) {
            return;
        }

        if (requestCode == LOAD_PAYMENT_DATA_REQUEST_CODE)
            SNTPClient.getDate("time.google.com", sntpListener);//we get date from google in a way that doesn't cause ANR
        else
            SNTPClient.getDateANR("time.google.com", sntpListener);//we get date from google in a way that causes ANR

        JSONObject paymentMethodData;
        try {
            paymentMethodData = new JSONObject(paymentInformation).getJSONObject("paymentMethodData");
            puListener.onSuccess(paymentMethodData);//yay, we did it right, returning the callback

            String billingName =
                    paymentMethodData.getJSONObject("info").getJSONObject("billingAddress").getString("name");
            Log.d("BillingName", billingName);
            Toast.makeText(context, billingName, Toast.LENGTH_LONG).show();

            // Logging token string.
            Log.d("GooglePaymentToken", paymentMethodData.getJSONObject("tokenizationData").getString("token"));
        } catch (JSONException e) {
            Log.e("handlePaymentSuccess", "Error: " + e.toString());
            //return;
        }
    }

    /**
     * checks and handles our testing paying
     * <p>
     * If the gateway is set to "example", no payment information is returned - instead, the
     * token will only consist of "examplePaymentMethodToken".
     */
    public void handleTestPayment(JSONObject paymentMethodData, Context context) throws JSONException {
        if (paymentMethodData
                .getJSONObject("tokenizationData")
                .getString("type")
                .equals("PAYMENT_GATEWAY")
                && paymentMethodData
                .getJSONObject("tokenizationData")
                .getString("token")
                .equals("examplePaymentMethodToken")) {
            AlertDialog alertDialog =
                    new AlertDialog.Builder(context)
                            .setTitle("Warning")
                            .setMessage(
                                    "Gateway name set to \"example\" - please modify "
                                            + "Constants.java and replace it with your own gateway.")
                            .setPositiveButton("OK", null)
                            .create();
            alertDialog.show();
        }
    }

    /**
     * At this stage, the user has already seen a popup informing them an error occurred. Normally,
     * only logging is required.
     *
     * @param statusCode will hold the value of any constant from CommonStatusCode or one of the
     *                   WalletConstants.ERROR_CODE_* constants.
     * @see <a
     * href="https://developers.google.com/android/reference/com/google/android/gms/wallet/WalletConstants#constant-summary">
     * Wallet Constants Library</a>
     */
    private void handleError(int statusCode) {
        Log.w("loadPaymentData failed", String.format("Error code: %d", statusCode));
    }

}