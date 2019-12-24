package lt.myapp.myapp;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import android.util.Log;
import android.view.View;

import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import lt.myapp.myapp.Payment.PaymentsUtil;
import lt.myapp.myapp.sys.TimeToolsExtra.SNTPClient;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private View mGooglePayButton;
    private View mGooglePayANRButton;
    private int customRequestCode = PaymentsUtil.LOAD_PAYMENT_DATA_REQUEST_CODE; //by default it's default
    private int customRequestCodeANR = 900; //by default it's default
    private boolean googleInitialized = false;//have we initialized already?
    private JSONObject mPaymentMethodData = null;//our payment data
    private PaymentsUtil pu = null;
    private static long GOOGLE_PAY_PRICE = 1000000;
    private TextView mGooglePayStatusText = null;

    private boolean gotTime = false;//did we get time from server yet


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        mGooglePayStatusText = findViewById(R.id.google_unavailable_text);
        mGooglePayButton = findViewById(R.id.google_pay_button);
        mGooglePayANRButton = findViewById(R.id.google_pay_anr_button);
        initGooglePayButton();


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void initGooglePayButton() {
        if (!googleInitialized) {
            googleInitialized = true;
            mGooglePayButton.setOnClickListener(gPay -> {
                mPaymentMethodData = null;//we clean whatever we had
                pu.requestPayment(gPay, this, GOOGLE_PAY_PRICE, customRequestCode);
            });//performs pay operation
            mGooglePayANRButton.setOnClickListener(gPay -> {
                mPaymentMethodData = null;//we clean whatever we had
                pu.requestPayment(gPay, this, GOOGLE_PAY_PRICE, customRequestCodeANR);
            });//performs pay operation

            pu = new PaymentsUtil(this);
            pu.possiblyShowGooglePayButton(new OnCompleteListener<Boolean>() {
                @Override
                public void onComplete(@NonNull Task<Boolean> task) {
                    if (task.isSuccessful()) {
                        setGooglePayAvailable(task.getResult());
                    } else {
                        setGooglePayAvailable(false);
                        //System.out.println("isReadyToPay failed: " + task.getException());
                        Log.w("isReadyToPay failed", task.getException());
                    }
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        System.out.println("Testing: onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == customRequestCode || requestCode == customRequestCodeANR) {//PaymentsUtil.LOAD_PAYMENT_DATA_REQUEST_CODE
            pu.onActivityResult(requestCode, resultCode, data, mGooglePayButton,
                    new SNTPClient.Listener() {
                        @Override
                        public void onTimeReceived(long requestTime, long serverTime, long offset) {
                            try {
                                System.out.println("Testing: onTimeReceived");
                                //todo: uncommenting this toast solves ANR, now wtf is that all about?
                                //Toast.makeText(mGooglePayButton.getContext(), "onTimeReceived done", Toast.LENGTH_LONG).show();//to make same environment as in PaymentsUtil
                                gotTime = true;//our synchronisation thingy
                                processPayment(mPaymentMethodData);
                            }catch (Exception e){
                                Log.e(SNTPClient.TAG, e.getMessage());
                            }
                            //Log.e(SNTPClient.TAG, rawDate);
                        }

                        @Override
                        public void onError(long requestTime, Exception ex) {
                            System.out.println("Testing: onError");
                            gotTime = true;//our synchronisation thingy
                            processPayment(mPaymentMethodData);
                            Log.e(SNTPClient.TAG, ex.getMessage());
                        }
                    },

                    /**
                     * If payment succeeds, we get this listener called out
                     */
                    new PaymentsUtil.PUListener() {
                        @Override
                        public void onSuccess(JSONObject paymentMethodData) {
                            System.out.println("Testing: onSuccess");
                            mPaymentMethodData = paymentMethodData;//used when we finish receiving ntp date!
                            processPayment(paymentMethodData);
                        }
                    });

        }
    }

    private void setGooglePayAvailable(boolean available) {
        if (available) {
            mGooglePayStatusText.setVisibility(View.INVISIBLE);
            mGooglePayButton.setVisibility(View.VISIBLE);
            //} else {//we do nothing
        }else{
            mGooglePayStatusText.setVisibility(View.VISIBLE);
            mGooglePayButton.setVisibility(View.INVISIBLE);
        }

    }

    private void processPayment(JSONObject paymentMethodData){
        System.out.println("Testing: processPayment. paymentMethodData="+paymentMethodData+", gotTime="+gotTime);
        if (paymentMethodData != null && gotTime) {
            try {
                pu.handleTestPayment(paymentMethodData, this);//just for testing purpose with
            } catch (Exception e) {
                System.out.println("Testing e:" + e.getMessage());
            }
        }
    }



    @Override
    public void onBackPressed() {
        System.out.println("Testing: onBackPressed");
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
