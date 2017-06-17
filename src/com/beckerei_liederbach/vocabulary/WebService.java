package com.beckerei_liederbach.vocabulary;

import java.net.ServerSocket;
import java.net.Socket;

public class WebService extends Thread
{
	private ServerSocket acceptSocket;
	VokabelGameHaupt vokabelGameHaupt;

	WebService(VokabelGameHaupt vokabelGameHaupt, int port) throws Exception
	{
		this.vokabelGameHaupt = vokabelGameHaupt;
		acceptSocket = new ServerSocket(port);
		setName("WebService TCP listener at port " + port);
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
