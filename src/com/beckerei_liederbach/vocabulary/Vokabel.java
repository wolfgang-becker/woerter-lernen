package com.beckerei_liederbach.vocabulary;

public class Vokabel {
	String Fremdwort;
	String Deutsch;
	String BuchLektion;
	long   lastAskTime;
	int    numKnownsInSeq;
	int    numKnowsInSequence;

	public Vokabel(String Fremdwort, String Deutsch, String BuchLektion, long lastAskTime, int numKnowsInSequence) {
		this.Fremdwort = Fremdwort;
		this.Deutsch = Deutsch;
		this.BuchLektion = BuchLektion;
		this.lastAskTime = lastAskTime;
		this.numKnowsInSequence = numKnowsInSequence;
	}

}
