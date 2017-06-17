package com.beckerei_liederbach.vocabulary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class VokabelGameHaupt
{
	private static final String BOOK_DIR = "src/resources/books/";
	Map<String,QuestionSession> questionSessions = new HashMap<>(); // session key = user:pass:book:unit
	Random                      zufallszahlen = new Random();
	Connection                  con; // database connection
	private Map<String, String> userAccounts = new HashMap<>();

	public static void main(String[] args) throws Exception
	{
		new VokabelGameHaupt(args);
	}

	public VokabelGameHaupt(String[] args) throws Exception
	{
		String databaseDir = null;
		int port = 80;
		for (int a = 0; a < args.length; a++) {
			switch(args[a]) {
			case "-dbdir": databaseDir = args[++a]; break;
			case "-port": port = Integer.parseInt(args[++a]); break;
			default: throw new Exception("invalid command line arg '" + args[a] + "'");
			}
		}

		openOrCreateDatabase(databaseDir);

		new WebService(this, port); // this object listens to requests from the Internet

		new SessionSaver(this);
		
		String ratingAndNextQuestion[] = new String[2];
		while (true){
			System.out.print(ratingAndNextQuestion[1] + " Übersetzung :  ");
			BufferedReader bur = new BufferedReader(new InputStreamReader(System.in));			
			String TastaturEingabe = bur.readLine();
			evaluateAnswerAndGetNextQuestion("Felix", "FelixSecret", "Latein_Prima_Vokabeln/prima6", "prima6_13", ratingAndNextQuestion[1], TastaturEingabe, ratingAndNextQuestion);
			System.out.println(ratingAndNextQuestion[0]);
		}

	}

	private boolean vergleiche(String Eingabe, String deutsch) {
		String[] alternativen = deutsch.split("[,/]",-1);
		if (alternativen.length > 1 ){
			for (String alternative : alternativen) {
				if (vergleiche(Eingabe, alternative.trim())){
					return true;
				}
			}
		}
		else if (Eingabe.equalsIgnoreCase(deutsch)){
			return true;
		}
		String DeutschOhneDasInDenKlammern = deutsch.replaceAll("\\(.*\\)", "").replaceAll("-","").trim();
		if (Eingabe.equalsIgnoreCase(DeutschOhneDasInDenKlammern)){
			return true;
		}
		String DeutschohneKlammern = deutsch.replaceAll("\\(", "").replaceAll("\\)","").trim();
		if (Eingabe.equalsIgnoreCase(DeutschohneKlammern)){
			return true;
		}
		return false;
	}

	public void evaluateAnswerAndGetNextQuestion(String user, String pass, String bookName, String unitName, String question, String answer, String ratingAndNextQuestion[]) throws Exception
	{
		if (!authenticateUser(user, pass)) {
			ratingAndNextQuestion[0] = "Wrong password for this user";
			ratingAndNextQuestion[1] = "";
			return;
		}
		String sessionKey = QuestionSession.getKey(user, bookName, unitName);
		QuestionSession questionSession = getOrOpenQuestionSession(sessionKey, user, bookName, unitName);
		// finde die Zeile mit der Frage
		Vokabel gefragteVokabel = null;
		int ZeilenNummer = 0;
		for (; ZeilenNummer < questionSession.vokabeln.size(); ZeilenNummer++) {
			Vokabel vokabel = questionSession.vokabeln.get(ZeilenNummer);
			if (vokabel.Fremdwort.equals(question)) {
				gefragteVokabel = vokabel;
				break;
			}
		}
		if (gefragteVokabel == null) {
			ratingAndNextQuestion[0] = "Couldn't find the question";
		}
		else if (vergleiche(answer, gefragteVokabel.Deutsch)) {
			Vokabel word = questionSession.vokabeln.remove(ZeilenNummer);
			questionSession.vokabeln.add(word);
			questionSession.wordIndexNoLongerAsk--;
			word.lastAskTime = System.currentTimeMillis();
			word.numKnownsInSeq++;
			ratingAndNextQuestion[0] = "Richtig! [" + gefragteVokabel.Deutsch + "]\r\n" +
			                           (word.numKnownsInSeq > 1 ? (word.numKnownsInSeq + " mal in Folge gewusst\r\n") : "") +
                                       "Noch " + questionSession.wordIndexNoLongerAsk + " Wörter.";
		} else {
			ratingAndNextQuestion[0] = gefragteVokabel.Fremdwort + " = <font color=\"red\"><b>" + gefragteVokabel.Deutsch + "</b></font>";
			questionSession.vokabeln.get(ZeilenNummer).lastAskTime = System.currentTimeMillis();
			questionSession.vokabeln.get(ZeilenNummer).numKnownsInSeq = 0;
		}
		if (questionSession.wordIndexNoLongerAsk <= 0) {
			ratingAndNextQuestion[1] = "Fertig, Du kennst alle Wörter! Gratuliere!";
			questionSession.saveToDatabase(con);
			questionSessions.remove(questionSession.getKey());
		} else { // suche die naechste Frage aus
			int neueZeilenNummer = zufallszahlen.nextInt(questionSession.wordIndexNoLongerAsk);
			ratingAndNextQuestion[1] = questionSession.vokabeln.get(neueZeilenNummer).Fremdwort;
		}
	}

	private QuestionSession getOrOpenQuestionSession(String sessionKey, String user, String book, String unit) throws Exception
	{
		if (book.isEmpty()) throw new Exception("Please select a book");
		if (unit.isEmpty()) throw new Exception("Please select a unit in the book");
		QuestionSession questionSession = questionSessions.get(sessionKey);
		if (questionSession != null) {
			questionSession.lastInteractionTimeStamp = System.currentTimeMillis();
			return questionSession; // already cached in memory
		}
		questionSession = new QuestionSession(user, book, unit);
		String bookKey = book + "/" + unit;
		try (FileReader fr = new FileReader(BOOK_DIR + "/" + bookKey + ".txt")) {
			try (BufferedReader br = new BufferedReader(fr)) {
				while (true){
					String zeile = br.readLine();
					if (zeile == null) break;
					String[] FremdwortDeutschBuch = zeile.split(";",-1);
					Vokabel vokabel = new Vokabel(FremdwortDeutschBuch[0],FremdwortDeutschBuch[1],FremdwortDeutschBuch[2], 0, 0);
					questionSession.vokabeln.add(vokabel);
				}
			} 
		} catch (Exception e) {
			throw new Exception("Book " + bookKey + " not found in library");
		}
		questionSession.wordIndexNoLongerAsk = questionSession.vokabeln.size();
		// move the questions that have been answered correctly recently (or often enough) to the end of the list (the one's that don't get asked)
		long now = System.currentTimeMillis();
		synchronized(con) {
			boolean sessionWasNotYetStoredInDatabase = true;
			try (PreparedStatement s = con.prepareStatement("select foreign_word, last_ask_time, num_knowns_in_seq " +
                                                            "from USER_WORD_STATUS " +
                                                            "where username = ? and book = ? and unit = ?")) {
				s.setString(1, user);
				s.setString(2, book);
				s.setString(3, unit);
				try (ResultSet r = s.executeQuery()) {
					while (r.next()) {
						sessionWasNotYetStoredInDatabase = false;
						String foreignWord = r.getString(1);
						long lastAskTime = r.getLong(2);
						int numKnownsInSeq = r.getInt(3);
						int indexInMemory = 0;
						while (indexInMemory < questionSession.vokabeln.size() && !questionSession.vokabeln.get(indexInMemory).Fremdwort.equals(foreignWord)) indexInMemory++;
						if (indexInMemory >= questionSession.vokabeln.size()) throw new Exception("find word '" + foreignWord + "' in database but not in the book/unit");
						Vokabel word = questionSession.vokabeln.get(indexInMemory);
						word.lastAskTime    = lastAskTime   ;
						word.numKnownsInSeq = numKnownsInSeq;
						long hoursSinceLastAsked = (now - lastAskTime) / 1000 / 60 / 60;
						long daysSinceLastAsked = hoursSinceLastAsked / 24;
						if ((numKnownsInSeq >= 8 && daysSinceLastAsked  < 60) ||
							(numKnownsInSeq >= 5 && daysSinceLastAsked  <  7)	||
							(numKnownsInSeq >= 3 && hoursSinceLastAsked < 24)	||
							(numKnownsInSeq >= 2 && hoursSinceLastAsked <  2)) { // move it back to the end, update the border index
							questionSession.vokabeln.remove(indexInMemory);
							questionSession.vokabeln.add(word);
							questionSession.wordIndexNoLongerAsk--;
						}
					}
				}
			}
			if (sessionWasNotYetStoredInDatabase) { // store all questions into the database, so that we can simply them update later on (insert-or-update is complicated with SQL)
				try (PreparedStatement s = con.prepareStatement("insert into USER_WORD_STATUS (username, book, unit, foreign_word, last_ask_time, num_knowns_in_seq) values (?,?,?,?,0,0)")) {
					for (Vokabel word : questionSession.vokabeln) {
						s.setString(1, user);
						s.setString(2, book);
						s.setString(3, unit);
						s.setString(4, word.Fremdwort);
						s.addBatch();
					}
					s.executeBatch();
					con.commit();
				}
			}
		}
		questionSessions.put(sessionKey, questionSession);
		return questionSession;
	}


	private void openOrCreateDatabase(String databaseDir) throws Exception
	{
		if (databaseDir == null) throw new Exception("databaseDir is null");
		DriverManager.registerDriver(new org.apache.derby.jdbc.EmbeddedDriver());
		con = DriverManager.getConnection("jdbc:derby:directory:" + databaseDir + ";create=true", "", "");
		con.setAutoCommit(false);
		// lookup which tables exist and create missing tables
		Set<String> existingTables = new HashSet<>();
		DatabaseMetaData dbmd = con.getMetaData();
		try (ResultSet resultSet = dbmd.getTables(null, null, null, null)) {
			while (resultSet.next()) existingTables.add(resultSet.getString("TABLE_NAME"));
		}
		try (Statement stmt = con.createStatement()) {
			if (!existingTables.contains("USERS")) {
				System.out.println("create table USERS");
				stmt.executeUpdate("create table USERS(username varchar(100) primary key, password varchar(100), status varchar(100), email varchar(100), role varchar(100))");
				stmt.executeUpdate("insert into USERS(username,password,status,email,role) values ('admin','admin','ok','wolfgang.becker@vodafone.de','admin')");
				con.commit();
			}
			if (!existingTables.contains("USER_WORD_STATUS")) {
				System.out.println("create table USER_WORD_STATUS");
				stmt.executeUpdate("create table USER_WORD_STATUS(username varchar(100), book varchar(100), unit varchar(100), foreign_word varchar(4000), last_ask_time bigint, num_knowns_in_seq int, " +
			                       "primary key (username, book, unit , foreign_word))");
			}
		}
	}

	public List<String> listBooks()
	{
		List<String> books = new ArrayList<>();
		for (File seriesDir : new File(BOOK_DIR).listFiles()) { // Latein_Prima etc
			for (File bookDir : seriesDir.listFiles()) { // prima7 etc
				books.add(seriesDir.getName() + "/" + bookDir.getName());
			}
		}
		return books;
	}

	public List<String> listUnits(String book)
	{
		List<String> units = new ArrayList<>();
		if (book.isEmpty()) return units;
		for (File unitsDir : new File((BOOK_DIR + "/" + book)).listFiles()) { // Latein_Prima/prima7 etc
			units.add(unitsDir.getName().replace(".txt", ""));
		}
		return units;		
	}

	private boolean authenticateUser(String user, String pass) throws SQLException
	{
		String expectedPass = userAccounts.get(user);
		if (expectedPass == null || !expectedPass.equals(pass)) { // load from database
			synchronized(con) {
				try (PreparedStatement s = con.prepareStatement("select password from USERS where username = ?")) {
					s.setString(1, user);
					try (ResultSet r = s.executeQuery()) {
						if (r.next()) {
							userAccounts.put(user, r.getString(1));
						}
					}
				}
			}
		}
		expectedPass = userAccounts.get(user);
		if (expectedPass == null) { // new user
			System.out.println("New user '" + user + "'");
			userAccounts.put(user, pass);
			synchronized(con) {
				try (PreparedStatement s = con.prepareStatement("insert into USERS (username, password) values (?,?)")) {
					s.setString(1, user);
					s.setString(2, pass);
					s.executeUpdate();
					con.commit();
				}
			}
			return true;
		}
		return expectedPass.equals(pass);
	}
}
