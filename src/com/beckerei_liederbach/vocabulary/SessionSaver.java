package com.beckerei_liederbach.vocabulary;

public class SessionSaver extends Thread
{
	private VokabelGameHaupt main;

	public SessionSaver(VokabelGameHaupt main)
	{
		this.main = main;
		setName("SessionSaver");
		start();
	}
	
	@Override
	public void run()
	{
		while (true) {
			try	{
				Thread.sleep(1000 * 60); // every minute sync status of sessions into database
			}
			catch (InterruptedException e) { e.printStackTrace(); }
			for (QuestionSession session : main.questionSessions.values()) {
				try {
					session.saveToDatabase(main.con);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				if (System.currentTimeMillis() > session.lastInteractionTimeStamp + 1000 * 60 * 10) { // after 10 minutes idle time remove session from cache
					System.out.println("Removing idle session " + session.getKey());
					try {
						main.questionSessions.remove(session.getKey());
					}
					catch (Exception e) { e.printStackTrace(); }
				}
			}
		}
	}
}
