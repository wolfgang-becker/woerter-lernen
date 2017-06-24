package com.beckerei_liederbach.vocabulary;

public class ScoreEntry
{
	String email;
	long lastAskTimeMSec = 0;
	int knownWords;
	int knownWords2;
	int knownWords3;
	int userCount;

	public ScoreEntry(int userCount)
	{
		this.userCount = userCount;
	}

	public void add(String email, long lastAskTimeMSec, int numKnownsInSeq)
	{
		this.email = email;
		this.lastAskTimeMSec = Math.max(this.lastAskTimeMSec, lastAskTimeMSec);
		if (numKnownsInSeq > 0) knownWords ++;
		if (numKnownsInSeq > 1) knownWords2++;
		if (numKnownsInSeq > 2) knownWords3++;
	}

	public double score()
	{
		// most important is how many words correct, then how many correct multiple times
		long msecSinceAsked = lastAskTimeMSec == 0 ? 0 : (System.currentTimeMillis() - lastAskTimeMSec);
		return knownWords + knownWords2 + knownWords3 - msecSinceAsked / 1000.0 / 60.0 / 60.0 / 7.0 + userCount / 1000.0; // one week inactivity costs one word, user count makes it unique, because TreeMap has no duplicates
	}

}
