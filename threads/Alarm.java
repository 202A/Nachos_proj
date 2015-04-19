package nachos.threads;

import nachos.machine.*;
 import java.util.Iterator;
 import java.util.TreeSet;
/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
   // System.out.println(Machine.timer().getTime());
        Iterator<TimePlusTread>  it=Threadset.iterator();
        while (it.hasNext()){
            TimePlusTread tpt=it.next();
            if (tpt.getwake_time()<=Machine.timer().getTime()){
                tpt.getThread().ready();
                it.remove();
            }   else
                break;
        }
        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	// for now, cheat just to get something working (busy waiting is bad)
        boolean intStatus = Machine.interrupt().disable();
        
        long wake_time = Machine.timer().getTime() + x;
      //  System.out.println(wake_time);
        TimePlusTread tpt=new TimePlusTread();
        tpt.setThread(KThread.currentThread());
        tpt.setwake_time(wake_time);
        Threadset.add(tpt);
        KThread.currentThread().sleep();
		      
        Machine.interrupt().restore(intStatus);
    }
    
    
    
    public class TimePlusTread implements Comparable{
        private KThread thread;
        private long wake_time;
        public KThread getThread(){
            return thread;
        }
        public void setThread(KThread thread){
            this.thread=thread;
        }
        public long getwake_time(){
            return wake_time;
        }
        public void setwake_time(long wake_time){
            this.wake_time=wake_time;
        }
        public int compareTo(Object p){
            long p_time=((TimePlusTread)p).getwake_time();
            if (this.wake_time<p_time)
                return -1;
            if (this.wake_time>p_time)
                return 1;
            return 0;
        }
    }
    
    
    TreeSet<TimePlusTread>  Threadset= new TreeSet<TimePlusTread>();
}
