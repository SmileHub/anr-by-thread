# anr-by-thread
ANR caused by pressing "Back" after thread has executed

The whole project is built to demonstrate 2 scenarios:
* In one Thread().start() is used to receive NTP time
* In another rxjava method used to achieve the same effect

However once the Thread().start() is used, the "back" button action fails an generates ANR.
The curiousity makes me wonder and be curious about the difference between those two methods and what is lack 
to have threaded way worth smoothly.

On the way i have few hypothesis and thoughts:
1) Could the ANR issue appear only on my device and no one else suffers the issue?
2) Pressing "back" somehow might relte to the Thread which might be not fully handled after it's completion
while rxjava way does something extra to handle it's Threads inside.
