# anr-by-thread
ANR caused by pressing "Back" after thread has executed.

The whole project is built to demonstrate 2 scenarios:
* In one Thread().start() is used to receive NTP time
* In another rxjava method used to achieve the same effect

<img width="250" src="https://imgur.com/RLzLNjc" />

The demo application is built from two mixed source codes:
1) Google Pay testing code taken from https://developers.google.com/pay/api/android/guides/tutorial#example
2) NTP time receive code taken from: https://github.com/aslamanver/sntp-client-android
and discussed at https://stackoverflow.com/questions/16787240/android-getting-the-date-and-time-from-a-ntp-server

Those codes are mixed in a way, that once Google Pay test proceeds, NTP time is received and both actions merge into a final result code.

However code to receive NTP time which initially was Thread().start() based generated ANR once "back" button was pressed after it's execution. So i built alternative rxjava way to handle the process, but the curiousity is still there - "what does it take for threaded code have same effect as rxjava way?" so that after it's execution pressing "back" wouldn't generate ANR.

However once the Thread().start() is used, the "back" button action fails an generates ANR.
The curiousity makes me wonder and be curious about the difference between those two methods and what is lack 
to have threaded way worth smoothly.

On the way i have few hypothesis and thoughts:
1) Could the ANR issue appear only on my device and no one else suffers the issue?
2) Pressing "back" somehow might relte to the Thread which might be not fully handled after it's completion
while rxjava way does something extra to handle it's Threads inside.
