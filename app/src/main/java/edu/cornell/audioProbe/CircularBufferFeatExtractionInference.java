package edu.cornell.audioProbe;


import android.content.Context;
import android.util.Log;

public class CircularBufferFeatExtractionInference<T> {
    private int qMaxSize;// max queue size
    private int fp = 0;  // front pointer
    private int rp = 0;  // rear pointer
    private int qs = 0;  // size of queue
    private T[] q;    // actual queue
    //private static Ml_Toolkit_Application appState;
    private T[] tempQ;

    //thread to write in the database
    private Thread t;

    private static final String TAG = "XYY_QUEUE";


    @SuppressWarnings("unchecked")
    public CircularBufferFeatExtractionInference(Context context, int size) {
        qMaxSize = size;
        fp = 0;
        rp = 0;
        qs = 0;
        q = (T[]) new Object[qMaxSize];
        tempQ = (T[]) new Object[1];
    }

    public T delete() {
        if (!emptyq()) {
            //will not decrease size to avoid race condition
            qs--;
            fp = (fp + 1) % qMaxSize;
            return q[fp];
        } else {
            return null;
        }
    }

    public synchronized void insert(T c) {
        //insert case; if the queue is full then we will just skip.
        //this is not a typical producer consumer where if queue is full we
        //we just stop producing. Data will always be produced by sensors.
        //We just cannot stop it. So, if anytime the queue is full then
        // we just drop the samples.
        //
        //If we forcefully put the new element forcefully in the queue the sequence will be broken.
        //
        //There is no reason to put the thread who is calling to sleep (or "wait") because we don't have space
        //And the calling thread is the sensing thread. Stopping it will be disasterous.
        //
        //tempQ[0] = c;
        if (!fullq()) {
            qs++;
            rp = (rp + 1) % qMaxSize;
            //System.arraycopy(tempQ, 0, q[rp], 0, tempQ.length);//q[rp]
            q[rp] = c;

            //Log.e(TAG, "Calling feature extaction or inference thread " + c.toString());
            notify();
            // start the delete thread and start copying becasue there is element

        }

        //
        //ELSE means frame gets dropped
        //there is not space in the queue
        //

        else
            Log.e(TAG, "Frame dropped for a full buffer");

        //else
        //System.err.println("Overflow\n");
    }


    public synchronized T deleteAndHandleData() {


        //means that buffer doesn't yet have appState.writeAfterThisManyValues elements so sleep
        if (emptyq()) {
            try {
                //Log.d(TAG, "No data feature extraction thread going to sleep" );
                notifyAll(); // this is needed to activate freeCMemory thread
                wait();
            } catch (InterruptedException e) {
                //System.out.println("InterruptedException caught");
            }
        }

        //Log.e(TAG, "Pop inference data" );
        //means there is data now
        return delete();
    }

    public synchronized void freeCMemory() {
        Log.e("Going for stop", "free C memory");
        if (!emptyq()) {
            try {
                Log.e("Going for stop", "Not empty yet free C memory");
                wait(); // wait because there is more data to process. We will wait until it becomes empty
            } catch (InterruptedException e) {
                //System.out.println("InterruptedException caught");
            }
        }

    }

    public boolean emptyq() {
        return qs == 0;
    }

    public boolean fullq() {
        return qs == qMaxSize;
    }

    public int getQSize() {
        return qs;
    }

    public void printq() {
        System.out.print("Size: " + qs +
                ", rp: " + rp + ", fp: " + fp + ", q: ");
        for (int i = 0; i < qMaxSize; i++)
            System.out.print("q[" + i + "]="
                    + q[i] + "; ");
        System.out.println();
    }


    //wrtier thread
    /*public class MyFileWriter extends Thread {

		CircularBufferFeatExtractionInference<T> obj;

		public MyFileWriter(CircularBufferFeatExtractionInference<T>  obj)
		{
			this.obj=obj;
			//new Thread(this, "Producer").start();
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			while(true) {
				//q.put(i++);
				Log.d(TAG, "Thread starteddddddddddddddddddddddddd" );
				obj.writeToFile();
			}

		}

	}*/


}