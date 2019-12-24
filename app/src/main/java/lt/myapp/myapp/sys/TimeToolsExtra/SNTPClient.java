package lt.myapp.myapp.sys.TimeToolsExtra;
/*
 * Original work Copyright (C) 2008 The Android Open Source Project
 * Modified work Copyright (C) 2019, Aslam Anver
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.os.SystemClock;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * {@hide}
 * <p>
 * Simple SNTP client class for retrieving network time.
 * <p>
 * Sample usage:
 * <pre>SntpClient client = new SntpClient();
 * if (client.requestTime("time.foo.com")) {
 *     long now = client.getNtpTime() + SystemClock.elapsedRealtime() - client.getNtpTimeReference();
 * }
 * </pre>
 * <p>
 * taken from https://github.com/aslamanver/sntp-client-android
 */
public class SNTPClient {

    public long getRequestTime() {
        return requestTime;
    }

    public boolean isRequestResult() {
        return requestResult;
    }

    public Exception getException() {
        return exception;
    }

    public interface Listener {
        /**
         * @param requestTime time sent to sntp server during request
         * @param serverTime  time after the sntp replied with offset evaluated
         * @param offset      the offset without tics
         */
        void onTimeReceived(long requestTime, long serverTime, long offset);

        /**
         * @param requestTime time at which the request was sent
         * @param ex
         */
        void onError(long requestTime, Exception ex);
    }

    public static final String TAG = "SntpClient";

    private static final int REFERENCE_TIME_OFFSET = 16;
    private static final int ORIGINATE_TIME_OFFSET = 24;
    private static final int RECEIVE_TIME_OFFSET = 32;
    private static final int TRANSMIT_TIME_OFFSET = 40;
    private static final int NTP_PACKET_SIZE = 48;

    private static final int NTP_PORT = 123;
    private static final int NTP_MODE_CLIENT = 3;
    private static final int NTP_VERSION = 3;

    // Number of seconds between Jan 1, 1900 and Jan 1, 1970
    // 70 years plus 17 leap days
    private static final long OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L;

    private long requestTime;

    //offset calculated from the response
    private long clockOffset;

    // system time computed from NTP server response
    private long mNtpTime;

    // value of SystemClock.elapsedRealtime() corresponding to mNtpTime
    private long mNtpTimeReference;

    // round trip time in milliseconds
    private long mRoundTripTime;

    // callback listener
    private Listener listener;

    private boolean requestResult = false;
    private Exception exception = null;

    /**
     * Construct SntpClient for retrieve time with callback
     *
     * @param listener callback listener after time received.
     */
    SNTPClient(Listener listener) {
        this.listener = listener;
    }

    /**
     * Sends an SNTP request to the given host and processes the response.
     *
     * @param host    host name of the server.
     * @param timeout network timeout in milliseconds.
     * @return true if the transaction was successful.
     */
    public /*boolean*/ void requestTime(String host, int timeout) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(timeout);
            InetAddress address = InetAddress.getByName(host);
            byte[] buffer = new byte[NTP_PACKET_SIZE];
            DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, NTP_PORT);

            // set mode = 3 (client) and version = 3
            // mode is in low 3 bits of first byte
            // version is in bits 3-5 of first byte
            buffer[0] = NTP_MODE_CLIENT | (NTP_VERSION << 3);

            // get current time and write it to the request packet
            requestTime = System.currentTimeMillis();
            long requestTicks = SystemClock.elapsedRealtime();
            writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime);

            socket.send(request);

            // read the response
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            long responseTicks = SystemClock.elapsedRealtime();
            long responseTime = requestTime + (responseTicks - requestTicks);

            // extract the results
            long originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET);
            long receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET);
            long transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET);
            long roundTripTime = responseTicks - requestTicks - (transmitTime - receiveTime);
            // receiveTime = originateTime + transit + skew
            // responseTime = transmitTime + transit - skew
            // clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime))/2
            //             = ((originateTime + transit + skew - originateTime) +
            //                (transmitTime - (transmitTime + transit - skew)))/2
            //             = ((transit + skew) + (transmitTime - transmitTime - transit + skew))/2
            //             = (transit + skew - transit + skew)/2
            //             = (2 * skew)/2 = skew
            clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2;
            // if (false) Log.d(TAG, "round trip: " + roundTripTime + " ms");
            // if (false) Log.d(TAG, "clock offset: " + clockOffset + " ms");

            // save our results - use the times on this side of the network latency
            // (response rather than request time)
            mNtpTime = responseTime + clockOffset;
            mNtpTimeReference = responseTicks;
            mRoundTripTime = roundTripTime;
        } catch (Exception e) {
            //if (false) Log.d(TAG, "request time failed: " + e);
            requestResult = false;
            exception = e;
            //listener.onError(requestTime, e);
            //return false;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        requestResult = true;//for our upgraded getDate call
        //return true;
    }

    /**
     * Returns the time computed from the NTP transaction.
     *
     * @return time value computed from NTP server response.
     */
    public long getNtpTime() {
        return mNtpTime;
    }

    public long getClockOffset() {
        return clockOffset;
    }

    /**
     * Returns the reference clock value (value of SystemClock.elapsedRealtime())
     * corresponding to the NTP time.
     *
     * @return reference clock corresponding to the NTP time.
     */
    public long getNtpTimeReference() {
        return mNtpTimeReference;
    }

    /**
     * Returns the round trip time of the NTP transaction
     *
     * @return round trip time in milliseconds.
     */
    public long getRoundTripTime() {
        return mRoundTripTime;
    }

    /**
     * Reads an unsigned 32 bit big endian number from the given offset in the buffer.
     */
    private long read32(byte[] buffer, int offset) {
        byte b0 = buffer[offset];
        byte b1 = buffer[offset + 1];
        byte b2 = buffer[offset + 2];
        byte b3 = buffer[offset + 3];

        // convert signed bytes to unsigned values
        int i0 = ((b0 & 0x80) == 0x80 ? (b0 & 0x7F) + 0x80 : b0);
        int i1 = ((b1 & 0x80) == 0x80 ? (b1 & 0x7F) + 0x80 : b1);
        int i2 = ((b2 & 0x80) == 0x80 ? (b2 & 0x7F) + 0x80 : b2);
        int i3 = ((b3 & 0x80) == 0x80 ? (b3 & 0x7F) + 0x80 : b3);

        return ((long) i0 << 24) + ((long) i1 << 16) + ((long) i2 << 8) + (long) i3;
    }

    /**
     * Reads the NTP time stamp at the given offset in the buffer and returns
     * it as a system time (milliseconds since January 1, 1970).
     */
    private long readTimeStamp(byte[] buffer, int offset) {
        long seconds = read32(buffer, offset);
        long fraction = read32(buffer, offset + 4);
        return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L);
    }

    /**
     * Writes system time (milliseconds since January 1, 1970) as an NTP time stamp
     * at the given offset in the buffer.
     */
    private void writeTimeStamp(byte[] buffer, int offset, long time) {
        long seconds = time / 1000L;
        long milliseconds = time - seconds * 1000L;
        seconds += OFFSET_1900_TO_1970;

        // write seconds in big endian format
        buffer[offset++] = (byte) (seconds >> 24);
        buffer[offset++] = (byte) (seconds >> 16);
        buffer[offset++] = (byte) (seconds >> 8);
        buffer[offset++] = (byte) (seconds);

        long fraction = milliseconds * 0x100000000L / 1000L;
        // write fraction in big endian format
        buffer[offset++] = (byte) (fraction >> 24);
        buffer[offset++] = (byte) (fraction >> 16);
        buffer[offset++] = (byte) (fraction >> 8);
        // low order bits should be random data
        buffer[offset++] = (byte) (Math.random() * 255.0);
    }

    /**
     * Makes a call to sntp server to retrieve timestamp, also provides result offset
     * @param sntpServer
     * @param _listener
     */
    public static void getDateANR(/*TimeZone _timeZone,*/ String sntpServer, Listener _listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SNTPClient sntpClient = new SNTPClient(_listener);
                sntpClient.requestTime(sntpServer, 5000);
                if (sntpClient.isRequestResult()) {

                    //long nowAsPerDeviceTimeZone = sntpClient.getNtpTime();


                    //if we want to return results in string formatted way
                    //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    //sdf.setTimeZone(_timeZone);
                    //String rawDate = sdf.format(nowAsPerDeviceTimeZone); // The output time is formatted according to ISO 8601 format.


                    // Log.e(TAG, _timeZone.getID());

                    //_listener.onTimeReceived(rawDate);
                    _listener.onTimeReceived(sntpClient.getRequestTime(), sntpClient.getNtpTime(), sntpClient.getClockOffset());
                }else{
                    _listener.onError(sntpClient.getRequestTime(), sntpClient.getException());
                }

            }
        }).start();
    }

    /**
     * Makes a call to sntp server to retrieve timestamp, also provides result offset
     * <p>
     * it's like the above function, except it doesn't produce ANR exception when after payment user tries to click "back" button
     * something is messed up with the above function.
     * Todo: explain why new Thread().start() makes "back" button generate ANR after the purchase, priority: interesting :D
     *
     * @param sntpServer
     * @param _listener
     */
    public static void getDate(String sntpServer, Listener _listener) {
        SNTPClient sntpClient = new SNTPClient(_listener);
        Completable.fromAction(() -> sntpClient.requestTime(sntpServer, 5000))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            if (sntpClient.isRequestResult())
                                _listener.onTimeReceived(sntpClient.getRequestTime(), sntpClient.getNtpTime(), sntpClient.getClockOffset());
                            else
                                _listener.onError(sntpClient.getRequestTime(), sntpClient.getException());
                        },
                        throwable -> {
                            throwable.printStackTrace();
                        }
                );

    }

}
