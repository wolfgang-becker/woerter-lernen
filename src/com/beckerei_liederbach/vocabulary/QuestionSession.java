package com.beckerei_liederbach.vocabulary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class QuestionSession
{
	String user;
	String book;
	String unit;
	List <Vokabel> vokabeln;
	int wordIndexNoLongerAsk; // starting this index words are no longer asked
	public long lastInteractionTimeStamp;
	
	QuestionSession(String user, String book, String unit)
	{
		this.user = user;
		this.book = book;
		this.unit = unit;
		vokabeln = new ArrayList<>();
		lastInteractionTimeStamp = System.currentTimeMillis();
	}

	public static String getKey(String user, String book, String unit)
	{
		return user + ":" + book + ":" + unit;
	}

	public String getKey()
	{
		return getKey(user, book, unit);
	}
	
	public void saveToDatabase(Connection con) throws SQLException
	{
		System.out.println("Saving session " + getKey());
		synchronized(con) {
			try (PreparedStatement s = con.prepareStatement("update USER_WORD_STATUS set last_ask_time = ?, num_knowns_in_seq = ? " +
					"where user = ? and book = ? and unit = ? and foreign_word = ?")) {
				for (Vokabel word : vokabeln) {
					s.setLong  (1, word.lastAskTime);
					s.setInt   (2, word.numKnowsInSequence);
					s.setString(3, user);
					s.setString(4, book);
					s.setString(5, unit);
					s.setString(6, word.Fremdwort);
					s.addBatch();
				}
				s.executeBatch();
				con.commit();
			}
		}
	}
}
