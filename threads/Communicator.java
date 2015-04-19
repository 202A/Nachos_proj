package nachos.threads;

import nachos.machine.*;
import nachos.threads.*;

public class Communicator {
    public Communicator() {
        lock = new Lock();
        speakCondition = new Condition(lock);
        listenCondition = new Condition(lock);
        anotherCondition = new Condition(lock);
    }

    public void speak(int word) {
        lock.acquire();
        ++speaker;
        while (listener == 0 || isSpeaking) {
            speakCondition.sleep();
        }
        isSpeaking = true;
        this.word = word;
        listenCondition.wakeAll();
        --speaker;
        anotherCondition.sleep();
        lock.release();
    }

    public int listen() {
        lock.acquire();
        ++listener;
        while (!isSpeaking) {
            speakCondition.wake();
            listenCondition.sleep();
        }
        isSpeaking = false;
        int message = word;
        --listener;
        anotherCondition.wake();
        lock.release();
	return message;
    }

    private boolean isSpeaking = false;
    // private boolean notReceive = false;
    private int speaker = 0, listener = 0;
    private Lock lock;
    private Condition speakCondition, listenCondition, anotherCondition;
    private int word;
}
