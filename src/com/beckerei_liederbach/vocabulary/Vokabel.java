package com.beckerei_liederbach.vocabulary;

public class Vokabel {
	String Fremdwort;
	String Deutsch;
	long   lastAskTime;
	int    numKnowsInSequence;

	public Vokabel(String Fremdwort, String Deutsch, long lastAskTime, int numKnowsInSequence) {
		this.Fremdwort = Fremdwort.trim();
		this.Deutsch = Deutsch.trim();
		this.lastAskTime = lastAskTime;
		this.numKnowsInSequence = numKnowsInSequence;
	}

}
