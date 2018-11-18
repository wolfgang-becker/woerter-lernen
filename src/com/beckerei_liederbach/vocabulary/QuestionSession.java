package com.beckerei_liederbach.vocabulary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class QuestionSession
{
	String  email;
	String  book;
	String  unit;
	boolean toGerman;
	List <Vokabel> vokabeln;
	int wordIndexNoLongerAsk; // starting this index words are no longer asked
	public long lastInteractionTimeStamp;
	public int lastAnswerFieldRandomNumber; // to avoid resubmits with browser's "Back" button
	
	QuestionSession(String email, String book, String unit, boolean toGerman)
	{
		this.email = email;
		this.book = book;
		this.unit = unit;
		this.toGerman = toGerman;
		vokabeln = new ArrayList<>();
		lastInteractionTimeStamp = System.currentTimeMillis();
	}

	public static String getKey(String user, String book, String unit, boolean toGerman)
	{
		return user + ":" + book + ":" + unit + ":" + (toGerman ? "1" : "0");
	}

	public String getKey()
	{
		return getKey(email, book, unit, toGerman);
	}
	
	public void saveToDatabase(Connection con) throws SQLException
	{
		System.out.println("Saving session " + getKey());
		synchronized(con) {
			try (PreparedStatement s = con.prepareStatement("update USER_WORD_STATUS set last_ask_time = ?, num_knowns_in_seq = ? " +
					"where email = ? and book = ? and unit = ? and foreign_word = ? and to_german = ?")) {
				for (Vokabel word : vokabeln) {
					s.setLong   (1, word.lastAskTime);
					s.setInt    (2, word.numKnowsInSequence);
					s.setString (3, email);
					s.setString (4, book);
					s.setString (5, unit);
					s.setString (6, word.Fremdwort);
					s.setBoolean(7, toGerman);
					s.addBatch();
				}
				s.executeBatch();
				con.commit();
			}
		}
	}
}
