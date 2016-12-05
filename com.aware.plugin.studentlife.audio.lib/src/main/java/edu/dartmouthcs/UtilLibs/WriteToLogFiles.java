package edu.dartmouthcs.UtilLibs;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

//import org.devtcg.tools.logcat.LogcatActivity;
//import org.devtcg.tools.logcat.LogcatProcessor;

public class WriteToLogFiles
{


	private static final int MSG_NEWLINE = 1;
	private LogcatProcessor mLogcatter;
	private BufferedWriter writer = null;

	public WriteToLogFiles()
	{

		/*
		//creates a file

		//if(f.exists())f.delete();

		try
		{
			writer = new BufferedWriter(new FileWriter(f,true), 2048);//file will be appended always
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		finally
		{
			//if (writer == null)
				//try { writer.close(); } catch (IOException e) {}	
		}*/
	}

	//start log thread
	public void startLogThread()
	{
		mLogcatter = new LogcatProcessor()
		{
			public void onError(final String msg, Throwable e)
			{
				//error happens then we have nothing to do
			}

			public void onNewline(String line)
			{
				Message msg = mHandler.obtainMessage(MSG_NEWLINE);
				msg.obj = line;
				mHandler.sendMessage(msg);
			}
		};

		mLogcatter.start();
	}

	//stop the thread and process of logging also
	public void stopLogThread()
	{

		mLogcatter.stopCatter();
		mLogcatter.stop();
		mLogcatter = null;

		if(writer!=null){
			try {
				writer.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}



	//handler is called when inside the Handler thread we get a line
	//thread handles it to a handler and handler decides what to do
	//in our case it prints
	private final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			//			case MSG_ERROR:
			//				handleMessageError(msg);
			//				break;
			case MSG_NEWLINE:
				handleMessageNewline(msg);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};



	//make arrangements for writing this lines in a file
	private void handleMessageNewline(Message msg)
	{
		String line = (String)msg.obj;
		try {
			Log.w("written", "written to file");
			
			File f = new File(Environment.getExternalStorageDirectory(), "audiolog.log");
			writer = new BufferedWriter(new FileWriter(f,true), 2048);
			writer.write(" " + System.currentTimeMillis() + " "+ line + "\n");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}



	//inner class for thread, with abstarct methods on what to do when a new line comes or error happens
	public abstract class LogcatProcessor extends Thread
	{
		/* TODO: Support logcat filtering. */
		public final String[] LOGCAT_CMD = new String[] { "logcat","-v","time","AudioFlinger:W","*:S" };
		private static final int BUFFER_SIZE = 1024;

		private int mLines = 0;
		protected Process mLogcatProc = null;

		public void run()
		{
			try
			{
				mLogcatProc = Runtime.getRuntime().exec(LOGCAT_CMD);
			}
			catch (IOException e)
			{
				onError("Can't start " + LOGCAT_CMD[0], e);
				return;
			}

			BufferedReader reader = null;

			try
			{
				reader =
					new BufferedReader(new InputStreamReader(mLogcatProc.getInputStream()),
							BUFFER_SIZE);

				String line;

				while ((line = reader.readLine()) != null)
				{
					onNewline(line);
					mLines++;
				}

			}
			catch (IOException e)
			{
				onError("Error reading from process " + LOGCAT_CMD[0], e);
			}
			finally
			{
				if (reader != null)
					try { reader.close(); } catch (IOException e) {}

					stopCatter();
			}
		}

		public void stopCatter()
		{
			if (mLogcatProc == null)
				return;

			mLogcatProc.destroy();
			mLogcatProc = null;
		}

		public int getLineCount()
		{
			return mLines;
		}

		public abstract void onError(String msg, Throwable e);
		public abstract void onNewline(String line);
	}

}