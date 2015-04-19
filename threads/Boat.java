package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    static BoatGrader bg;
    static int childLeft;
    static int adultLeft;
    static int boatPos = 0;
    static int pilotGot = 0;
    
    static Lock mutex = new Lock();
    static Condition2 adultGo = new Condition2(mutex);
    static Condition2 childGo = new Condition2(mutex);
    static Condition2 childBack = new Condition2(mutex);
    static Condition2 done = new Condition2(mutex);
    
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
	System.out.println("\n ***Testing Boats with only 2 children***");
	begin(0, 20, b);

//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//  	begin(1, 2, b);

//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
//  	begin(3, 3, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;

	// Instantiate global variables here
	
	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.
	
	childLeft = children;
	adultLeft = adults;
	mutex.acquire();

	Runnable runChild = new Runnable() {
	    public void run() {
                ChildItinerary();
            }
        };
    Runnable runAdult = new Runnable() {
    	public void run() {
    			AdultItinerary();
    		}
    	};
    
    for (int i = 0; i < children; ++i) {
    	KThread child = new KThread(runChild);
        child.setName("Child " + i);
        child.fork();
    }
    
    for (int i = 0; i < adults; ++i) {
    	KThread adult = new KThread(runAdult);
        adult.setName("Adult " + i);
        adult.fork();
    }
    
    while (!(childLeft == 0 && adultLeft == 0)) {
    	done.sleep();
    }
    mutex.release();

    }

    static void AdultItinerary()
    {
	bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
	
	int myPos = 0;
	mutex.acquire();
	while (!(childLeft == 1 && boatPos == 0 && myPos == 0)) {
		adultGo.sleep();
    }
    bg.AdultRowToMolokai();
    myPos = 1;
    adultLeft--;
    boatPos = 1;
    if (childLeft == 0 && adultLeft == 0) {
    	done.wake();
    }
    if (boatPos == 1) {
    	childBack.wake();
    }
    mutex.release();
    }

    static void ChildItinerary()
    {
		bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE. 
	
		int myPos = 0;
		int peopleLeft = 0;
		while (true) {
			mutex.acquire();
			if (myPos == 0) {
				while (!(childLeft >= 2 && boatPos == 0) && 
					!(childLeft == 1 && adultLeft == 0 && boatPos == 0)) {
					childGo.sleep();
				}
				if (pilotGot == 0) {
					bg.ChildRowToMolokai();
					if (childLeft == 1) {
						boatPos = 1;
						childLeft--;
						peopleLeft = childLeft + adultLeft;
					} else {
						pilotGot = 1;
						peopleLeft = childLeft + adultLeft - 2;
					}
				} else {
					bg.ChildRideToMolokai();
					pilotGot = 0;
					boatPos = 1;
					childLeft = childLeft - 2;
					peopleLeft = childLeft + adultLeft;
				}
				myPos = 1;
				if (adultLeft == 0 && childLeft == 0) {
					done.wake();
				}
				if (boatPos == 1) {
					childBack.wake();
				}
			} else {
				while (!(boatPos == 1 && peopleLeft > 0)) {
					childBack.sleep();
				}
				bg.ChildRowToOahu();
				childLeft++;
				boatPos = 0;
				myPos = 0;
				if (boatPos == 0) {
					adultGo.wake();
				}
				if (boatPos == 0) {
					childGo.wake();
				}
			}
			mutex.release();
		}
    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }
    
}
