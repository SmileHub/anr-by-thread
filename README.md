# anr-by-thread
ANR caused by pressing "Back" after thread has executed.

The whole project is built to demonstrate 2 scenarios:
* In one Thread().start() is used to receive NTP time
* In another rxjava method used to achieve the same effect

<img width="250" src="https://i.imgur.com/RLzLNjc.jpg" />

The demo application is built from two mixed source codes:
1) Google Pay testing code taken from https://developers.google.com/pay/api/android/guides/tutorial#example
2) NTP time receive code taken from: https://github.com/aslamanver/sntp-client-android
and discussed at https://stackoverflow.com/questions/16787240/android-getting-the-date-and-time-from-a-ntp-server

Those codes are mixed in a way, that once Google Pay test proceeds, NTP time is received and both actions merge into a final result code.

When the Thread().start() way is being used, the "back" button action fails an generates ANR. Test case scenario:
1) going through thread based google pay test scenario
2) open the menu tab (so that pressing back wouldn't exit the app but supposingly close the menu)
<img width="250" src="https://i.imgur.com/IXh5WLL.jpg" />
3) press back button = ANR
<img width="250" src="https://i.imgur.com/rdwYUeQ.jpg" />

The alternative to threaded way was rxjava way to handle the process - for testing purpose.

The curiousity makes me wonder and be curious about:
* what is the difference between those two methods and what is lack to have threaded way worth smoothly.
* what does it take for threaded code have same effect as rxjava way? so that after it's execution pressing "back" wouldn't generate ANR.

On the way i have few hypothesis and thoughts:
1) Could the ANR issue appear only on my device and no one else suffers the issue?
2) Pressing "back" somehow might relte to the Thread which might be not fully handled after it's completion
while rxjava way does something extra to handle it's Threads inside.

Bonus:

What is more interesting is that if I uncomment the Toast in main activity ANR case disappears, although Toast isn't displayed as it's called from the non UI thread:

//Toast.makeText(mGooglePayButton.getContext(), "onTimeReceived done", Toast.LENGTH_LONG).show();//to make same environment as in PaymentsUtil
