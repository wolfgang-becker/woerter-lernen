package com.beckerei_liederbach.vocabulary;

import java.net.ServerSocket;
import java.net.Socket;

public class WebService extends Thread
{
	private static int PORT = 8081;
	private ServerSocket acceptSocket;
	VokabelGameHaupt vokabelGameHaupt;

	WebService(VokabelGameHaupt vokabelGameHaupt) throws Exception
	{
		this.vokabelGameHaupt = vokabelGameHaupt;
		acceptSocket = new ServerSocket(PORT);
		setName("WebService TCP listener at port " + PORT);
		start();
	}

	@Override
	public void run()
	{
		while (true) {
			try	{
				Socket cs = acceptSocket.accept();
				new WebServiceProcessRequest(this, cs);
			}
			catch (Throwable e)	{
				e.printStackTrace();
			}
		}
	}
}
