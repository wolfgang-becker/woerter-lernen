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
import java.util.concurrent.ConcurrentHashMap;

public class VokabelGameHaupt
{
	private static final String BOOK_DIR = "src/resources/books/";
	Map<String,QuestionSession> questionSessions = new ConcurrentHashMap<>(); // session key = user:pass:book:unit
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
		
		// on the console we can enter a question + answer to see the result for debugging
		String ratingAndNextQuestion[] = new String[2];
		while (true){
			System.out.print(ratingAndNextQuestion[1] + " Übersetzung :  ");
			BufferedReader bur = new BufferedReader(new InputStreamReader(System.in));			
			String TastaturEingabe = bur.readLine();
			evaluateAnswerAndGetNextQuestion("Felix@email", "FelixSecret", "Latein_Prima_Vokabeln/prima6", "prima6_13", ratingAndNextQuestion[1], TastaturEingabe, ratingAndNextQuestion, true);
			System.out.println(ratingAndNextQuestion[0]);
		}

	}

	private boolean vergleiche(String givenAnswer, String correctAnswer) {
		String[] alternativen = correctAnswer.split("[,/]",-1);
		if (alternativen.length > 1 ){
			for (String alternative : alternativen) {
				if (vergleiche(givenAnswer, alternative.trim())){
					return true;
				}
			}
		}
		else if (givenAnswer.equalsIgnoreCase(correctAnswer)){
			return true;
		}
		String DeutschOhneDasInDenKlammern = correctAnswer.replaceAll("\\(.*\\)", "").replaceAll("-","").trim();
		if (givenAnswer.equalsIgnoreCase(DeutschOhneDasInDenKlammern)){
			return true;
		}
		String DeutschohneKlammern = correctAnswer.replaceAll("\\(", "").replaceAll("\\)","").trim();
		if (givenAnswer.equalsIgnoreCase(DeutschohneKlammern)){
			return true;
		}
		return false;
	}

	/**
	 * @param toGerman 
	 * @return true if no error occurred
	 */
	public boolean evaluateAnswerAndGetNextQuestion(String email, String pass, String bookName, String unitName, String question, String answer, String ratingAndNextQuestion[], boolean toGerman) throws Exception
	{
		if (email.isEmpty()) {
			ratingAndNextQuestion[0] = "<font color='red'>Please enter email &amp; password.</font>";
			ratingAndNextQuestion[1] = "If you are new, just choose any.";
			return false;
		}
		if (!email.contains("@")) {
			ratingAndNextQuestion[0] = "<font color='red'>Email address must contain &#64;.</font>";
			return false;
		}
		if (pass.isEmpty()) {
			ratingAndNextQuestion[0] = "<font color='red'>Please enter your password.</font>";
			ratingAndNextQuestion[1] = "If you are new, just choose any.";
			return false;
		}
		if (!authenticateUser(email, pass)) {
			ratingAndNextQuestion[0] = "Wrong password for this user.";
			ratingAndNextQuestion[1] = "If you don't remember, create a new account.";
			return false;
		}
		QuestionSession questionSession = null;
		String sessionKey = QuestionSession.getKey(email, bookName, unitName, toGerman);
		try {
			questionSession = getOrOpenQuestionSession(sessionKey, email, bookName, unitName, toGerman);
		}
		catch (Exception e) {
			ratingAndNextQuestion[0] = e.getMessage();
			return false;
		}
		
		if (!question.isEmpty()) { // find the question text line in the book/unit
			Vokabel gefragteVokabel = null;
			int ZeilenNummer = 0;
			for (; ZeilenNummer < questionSession.vokabeln.size(); ZeilenNummer++) {
				Vokabel vokabel = questionSession.vokabeln.get(ZeilenNummer);
				if ((toGerman && vokabel.Fremdwort.equals(question)) || (!toGerman && vokabel.Deutsch.equals(question))) {
					gefragteVokabel = vokabel;
					break;
				}
			}
			if (gefragteVokabel == null) {
				ratingAndNextQuestion[0] = "Couldn't find the question";
				return false;
			}
			else if (vergleiche(answer, toGerman ? gefragteVokabel.Deutsch : gefragteVokabel.Fremdwort)) {
				Vokabel word = questionSession.vokabeln.remove(ZeilenNummer);
				questionSession.vokabeln.add(word);
				questionSession.wordIndexNoLongerAsk--;
				word.lastAskTime = System.currentTimeMillis();
				word.numKnownsInSeq++;
				ratingAndNextQuestion[0] = question + " = <font color=\"green\">" + (toGerman ? gefragteVokabel.Deutsch : gefragteVokabel.Fremdwort) + "</font> " +
						(word.numKnownsInSeq > 1 ? ("<br/><font color=\"brown\">" + word.numKnownsInSeq + " x richtig!</font>") : "") +
						"<br/><font color=\"brown\">Noch " + questionSession.wordIndexNoLongerAsk + " Wörter</font>";
			} else {
				ratingAndNextQuestion[0] = question + " = <font color=\"red\"><b>" + (toGerman ? gefragteVokabel.Deutsch : gefragteVokabel.Fremdwort) + "</b></font>";
				questionSession.vokabeln.get(ZeilenNummer).lastAskTime = System.currentTimeMillis();
				questionSession.vokabeln.get(ZeilenNummer).numKnownsInSeq = 0;
			}
		} else { // got no question
			ratingAndNextQuestion[0] = "";
		}

		if (questionSession.wordIndexNoLongerAsk <= 0) {
			ratingAndNextQuestion[1] = "Fertig, Du kennst alle Wörter! Gratuliere!";
			questionSession.saveToDatabase(con);
			questionSessions.remove(questionSession.getKey());
			return false;
		} else { // suche die naechste Frage aus
			int neueZeilenNummer = zufallszahlen.nextInt(questionSession.wordIndexNoLongerAsk);
			ratingAndNextQuestion[1] = toGerman ? questionSession.vokabeln.get(neueZeilenNummer).Fremdwort : questionSession.vokabeln.get(neueZeilenNummer).Deutsch;
		}
		return true;
	}

	private QuestionSession getOrOpenQuestionSession(String sessionKey, String email, String book, String unit, boolean toGerman) throws Exception
	{
		if (book.isEmpty()) throw new Exception("Please select a book");
		if (unit.isEmpty()) throw new Exception("Please select a unit in the book");
		QuestionSession questionSession = questionSessions.get(sessionKey);
		if (questionSession != null) {
			questionSession.lastInteractionTimeStamp = System.currentTimeMillis();
			return questionSession; // already cached in memory
		}
		questionSession = new QuestionSession(email, book, unit, toGerman);
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
                                                            "where email = ? and book = ? and unit = ? and to_german = ?")) {
				s.setString (1, email   );
				s.setString (2, book    );
				s.setString (3, unit    );
				s.setBoolean(4, toGerman);
				try (ResultSet r = s.executeQuery()) {
					while (r.next()) {
						sessionWasNotYetStoredInDatabase = false;
						String foreignWord    = r.getString(1);
						long   lastAskTime    = r.getLong  (2);
						int    numKnownsInSeq = r.getInt   (3);
						int indexInMemory = 0;
						while (indexInMemory < questionSession.vokabeln.size() && !questionSession.vokabeln.get(indexInMemory).Fremdwort.equals(foreignWord)) indexInMemory++;
						if (indexInMemory >= questionSession.vokabeln.size()) {
							System.out.println("Found word '" + foreignWord + "' in database but not in the book/unit");
							continue;
						}
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
				try (PreparedStatement s = con.prepareStatement("insert into USER_WORD_STATUS (email, book, unit, foreign_word, last_ask_time, num_knowns_in_seq, to_german) values (?,?,?,?,0,0,?)")) {
					for (Vokabel word : questionSession.vokabeln) {
						s.setString (1, email);
						s.setString (2, book);
						s.setString (3, unit);
						s.setString (4, word.Fremdwort);
						s.setBoolean(5, toGerman);
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
				stmt.executeUpdate("create table USERS(email varchar(100) primary key, password varchar(100), status varchar(100), role varchar(100))");
				stmt.executeUpdate("insert into USERS(email,password,status,role) values ('wolfgang.becker@vodafone.de','admin','ok','admin')");
				con.commit();
			}
			if (!existingTables.contains("USER_WORD_STATUS")) {
				System.out.println("create table USER_WORD_STATUS");
				stmt.executeUpdate("create table USER_WORD_STATUS(email varchar(100), book varchar(100), unit varchar(100), foreign_word varchar(4000), last_ask_time bigint, num_knowns_in_seq int, " +
			                       "to_german boolean, primary key (email, book, unit , foreign_word, to_german))");
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

	private boolean authenticateUser(String email, String pass) throws SQLException
	{
		String expectedPass = userAccounts.get(email);
		if (expectedPass == null || !expectedPass.equals(pass)) { // load from database
			synchronized(con) {
				try (PreparedStatement s = con.prepareStatement("select password from USERS where email = ?")) {
					s.setString(1, email);
					try (ResultSet r = s.executeQuery()) {
						if (r.next()) {
							userAccounts.put(email, r.getString(1));
						}
					}
				}
			}
		}
		expectedPass = userAccounts.get(email);
		if (expectedPass == null) { // new user
			System.out.println("New user '" + email + "'");
			userAccounts.put(email, pass);
			synchronized(con) {
				try (PreparedStatement s = con.prepareStatement("insert into USERS (email, password) values (?,?)")) {
					s.setString(1, email);
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
