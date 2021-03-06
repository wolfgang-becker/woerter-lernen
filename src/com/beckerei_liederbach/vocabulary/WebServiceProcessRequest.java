package com.beckerei_liederbach.vocabulary;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.List;

public class WebServiceProcessRequest extends Thread
{
	private Socket cs;
	private WebService webService;

	public WebServiceProcessRequest(WebService revGeoCoder, Socket cs)
	{
		this.webService = revGeoCoder;
		this.cs = cs;
		setName("HTTP request processor at port " + cs.getLocalPort());
		start();
	}

	@Override
	public void run()
	{
		try { // to close cs at the end
			byte requestBody [] = new byte [1024 * 1024];
			String reqParams = "";
			String filePath = "";
			try (InputStream  is = cs.getInputStream();
                 OutputStream os = cs.getOutputStream()) {
				int i2, i3, a, headersEnd = -1;
				int nread;
				int requestBodyUsed = 0;
				while (headersEnd < 0 && requestBodyUsed < requestBody.length) {
					try {
						nread = is.read(requestBody, requestBodyUsed, requestBody.length - requestBodyUsed);
					}
					catch (IOException e) { e.printStackTrace(); return; }
					if (nread <= 0) { System.out.println(nread + " bytes read, skipping request\r\n"); return; }
					requestBodyUsed += nread;
					for (int i = 0; i < requestBodyUsed && headersEnd < 0; i++)	{
						if (requestBody[i]!='\n') continue;
						if (i + 1 < requestBodyUsed && requestBody[i + 1]=='\n') headersEnd = i + 2;
						if (i + 2 < requestBodyUsed && requestBody[i + 1]=='\r' && requestBody[i + 2] == '\n') headersEnd = i + 3;
					}
				}
				if (headersEnd < 0) { System.out.println("unrecognized request headers or request body too long (" + requestBodyUsed + " byte received), skipping request\r\n"); return; }
				// extract file path
				boolean isPost = (requestBody[0] == 'P');
				for (a = 0; a < headersEnd && requestBody[a] != '/'; a++) {} // skip GET and host part
				if (a < headersEnd - 1 && requestBody[a+1] == '/') { // GET http://a.b.c/path HTTP/1.0 -> proxy request
					for(a += 2; a < headersEnd && requestBody[a] != '/'; a++) {} // skip GET and host part
				}
				if (a > headersEnd) { System.out.println("unrecognized HTTP request\r\n"); return; }
				int i;
				for (i = a; i < headersEnd && ( (requestBody[i] >= 'A' && requestBody[i] <= 'Z') ||
						(requestBody[i]>='a' && requestBody[i]<='z') ||
						(requestBody[i]>='0' && requestBody[i]<='9') ||
						requestBody[i]=='_' || requestBody[i]=='.' || requestBody[i]=='/' || requestBody[i]=='?' || requestBody[i]=='&' || requestBody[i]=='=' || requestBody[i]=='%' || requestBody[i]=='+' || requestBody[i]=='-' || requestBody[i]=='*' || requestBody[i]=='@'); i++) {
					if (i<headersEnd-1 && requestBody[i]=='/' && requestBody[i+1]=='/') { System.out.println("Request contains // -> skipping request (hack attack)"); return; }
					if (i<headersEnd-1 && requestBody[i]=='.' && requestBody[i+1]=='.') { System.out.println("Request contains .. -> skipping request (hack attack)"); return; }
				}
				filePath = new String(requestBody, a, i - a);
				int qs = filePath.indexOf('?'); // extract request query string
				if (qs>=0) {
					reqParams = filePath.substring(qs+1);
					filePath = filePath.substring(0, qs);
				}
				try {
					filePath = URLDecoder.decode(filePath, "UTF-8");
				} catch (UnsupportedEncodingException e2) { e2.printStackTrace(); }
				int contentLengthValue = 0;
				while (i < headersEnd && requestBody[i]!='\r' && requestBody[i]!='\n') i++;
				while (i < headersEnd) { // parse request headers
					for (; i < requestBody.length && (requestBody[i]=='\r' || requestBody[i]=='\n'); i++) {}
					if (i >= headersEnd) break;
					for (i2 = i; i2 < headersEnd && requestBody[i2]!=':'; i2++) {}
					String name = new String(requestBody, i, i2-i).trim().toLowerCase();
					i2++;
					for (i3 = i2; i3 < headersEnd && requestBody[i3]!='\n'; i3++) {}
					String value = new String(requestBody, i2, i3-i2).trim();
					i = i3+1;
					if (name.equals("content-length")) {
						try {
							contentLengthValue = Integer.parseInt(value);
						} catch (NumberFormatException e) { e.printStackTrace(); }
					}
				}
				System.arraycopy(requestBody, headersEnd, requestBody, 0, requestBodyUsed - headersEnd);
				requestBodyUsed -= headersEnd;
				if (isPost && contentLengthValue == 0) {
					System.out.println("(Got POST request without content-length or content-length = 0)");
				}
				// System.out.println(requestBodyUsed + " bytes of request body recvd, expecting " + contentLengthValue);

				while (requestBodyUsed < requestBody.length && requestBodyUsed < contentLengthValue) {
					try	{
						nread = is.read(requestBody, requestBodyUsed, requestBody.length - requestBodyUsed);
					} catch (java.io.IOException e) { break; }
					if (nread<=0) break;
					// System.out.println(nread + " bytes request body recvd: \"" + new String(requestBody, requestBodyUsed, nread) + "\", expecting " + contentLengthValue);
					requestBodyUsed += nread;
				}
				if (isPost)	{
					reqParams += new String(requestBody, 0, requestBodyUsed);
				}

				StringBuffer outBuf = new StringBuffer();
				boolean binary = false;
				try { // to return errors to the user
					String email    = "";
					String pass     = "";
					String book     = "";
					String unit     = "";
					String question = "";
					String answer   = "";
					String language = "";
					String direction= "";
					boolean error = false;
					boolean showHighScores = false;
					boolean fastForward = false;
					int answerFieldRandomNumber = -1;
					for (String nameValue : reqParams.split("&", -1)) {
						String nameValueSplit[] = nameValue.split("=", -1); 
						String name = nameValueSplit[0];
						String value = nameValueSplit.length > 1 ? URLDecoder.decode(nameValueSplit[1], "ISO-8859-1").trim() : "";
						if (name.equals("book") || name.equals("unit")) {
							if (value.contains("\\") || value.startsWith("/") || value.contains(":") || value.contains("..")) {
								throw new Exception("No paths allowed in book or unit");
							}
						}
						if      (name.equals    ("email"      )) email          = value;
						else if (name.equals    ("pass"       )) pass           = value;
						else if (name.equals    ("book"       )) book           = value;
						else if (name.equals    ("unit"       )) unit           = value;
						else if (name.equals    ("question"   )) question       = value;
						else if (name.startsWith("answer"     )) { answer       = value; answerFieldRandomNumber = Integer.parseInt(name.substring(6)); } // answer2343
						else if (name.equals    ("direction"  )) direction      = value;
						else if (name.equals    ("score"      )) showHighScores = value.equals("y");
						else if (name.equals    ("fastforward")) fastForward    = value.equals("y");
					}
					boolean changeDirection = direction.contains("<-");
					if (!book.isEmpty() && book.contains("_")) language = book.substring(0, book.indexOf("_"));
					if (language.isEmpty()) {
						direction = "";
					}
					if (!language.isEmpty() && direction.isEmpty()) {
						direction = language.equals("Latein") ? (language + "->Deutsch") : ("Deutsch->" + language);
					}
					if (!language.isEmpty() && changeDirection) {
						if (direction.contains("<-Deutsch")) direction = "Deutsch->" + language;
						else                                 direction = language + "->Deutsch";
						question = ""; answer = "";
					}
					boolean toGerman = direction.contains("->Deutsch");

					// System.out.println("Body: ***\r\n" + new String(requestBody, 0, requestBodyUsed) + "***\r\n");

					binary = filePath.endsWith(".ico") || filePath.endsWith(".jpg");
					String contentType = "";
					if      (filePath.endsWith(".ico")) contentType = "image/x-icon";
					else if (filePath.endsWith(".jpg")) contentType = "image/jpeg";
					else if (filePath.endsWith(".css")) contentType = "text/css";
					else                                contentType = "text/html; charset=iso-8859-1";
					if (binary) os.write(("HTTP/1.0 200 OK\r\n" + "Content-Type: " + contentType + "\r\n" + "\r\n").getBytes("ISO-8859-1"));
					else outBuf.append("HTTP/1.0 200 OK\r\n" + "Content-Type: " + contentType + "\r\n" + "\r\n");

					System.out.println("## " + filePath + " ## " + reqParams);
					if (filePath.equals("/favicon.ico")) {
						loadWebPageBinary("favicon.ico", os);
					} else if (filePath.equals("/question") || filePath.equals("/") || filePath.isEmpty()) {
						String webPage = loadWebPage("next_question.html");
						String ratingAndNextQuestion[] = new String[2]; // die beiden Array Elemente werden von der Methode "evaluateAnswerAndGetNextQuestion" gefuellt
						String books = "<option value=\"" + book + "\">" + book + "</option>"; // the currently selected one
						String units = "<option value=\"" + unit + "\">" + unit + "</option>"; // the currently selected one
						try {
							List<String> books1 = webService.vokabelGameHaupt.listBooks();
							for (String b : books1) books += "<option value=\"" + b + "\">" + b + "</option>\r\n";
							List<String> units1 = webService.vokabelGameHaupt.listUnits(book);
							for (String u : units1) units += "<option value=\"" + u + "\">" + u + "</option>\r\n";
							error |= !webService.vokabelGameHaupt.evaluateAnswerAndGetNextQuestion(email, pass, book, unit, question, answer, answerFieldRandomNumber,
									                                                               ratingAndNextQuestion, toGerman, showHighScores, fastForward, direction);
						}
						catch (Exception e) {
							ratingAndNextQuestion[0] = e.getMessage();
							ratingAndNextQuestion[1] = "";
						}
						webPage = webPage.replaceAll("\\$\\{email\\}"       , email);
						webPage = webPage.replaceAll("\\$\\{pass\\}"        , pass);
						webPage = webPage.replaceAll("\\$\\{book\\}"        , books);
						webPage = webPage.replaceAll("\\$\\{unit\\}"        , units);
						webPage = webPage.replaceAll("\\$\\{direction\\}"   , direction);
						webPage = webPage.replaceAll("\\$\\{language\\}"    , language);
						webPage = webPage.replaceAll("\\$\\{rating\\}"      , ratingAndNextQuestion[0]);
						webPage = webPage.replaceAll("\\$\\{question\\}"    , ratingAndNextQuestion[1] == null ? "" : ratingAndNextQuestion[1]);
						webPage = webPage.replaceAll("\\$\\{showquestion\\}", error ? "display:none" : "display:initial");
						webPage = webPage.replaceAll("\\$\\{answer\\}", "answer" + (int)Math.floor(Math.random() * 10000)); // randomize the field name, otherwise the browser shows the history of answers
						if (showHighScores) {
							webPage = webPage.replaceAll("<input name=\"scorebutton\"", webService.vokabelGameHaupt.getHighScores(email, pass, book, unit, toGerman) + "</p><p><input name=\"scorebutton\"");
						}
						outBuf.append(webPage);
					} else if (filePath.matches("/felix/[A-Za-z0-9 \\-\\.]*") || filePath.matches("/felix/Bilder/[A-Za-z0-9 \\-\\.]*")) {
						if (filePath.endsWith(".jpg"))
							loadWebPageBinary(filePath.substring(1), os);
						else
							outBuf.append(loadWebPage(filePath.substring(1)));
					} else {
						outBuf.append("undefined file path");
					}
				} catch (Throwable e)	{
					e.printStackTrace();
					outBuf.append(e.getMessage());
				}
				if (!binary) os.write(outBuf.toString().getBytes("ISO-8859-1"));
				cs.setSoLinger(true, 60); // wait 60 seconds on close that browser can read the data
			}
		}
		catch (Exception e) { System.out.println("error processing request: " + e.getMessage()); }
		finally { try { cs.close(); } catch (IOException e) { System.out.println("error clsoing connect socket: " + e.getMessage());} }
	}

	private static String loadWebPage(String fileName) throws FileNotFoundException, IOException
	{
		StringBuffer alleZeilen = new StringBuffer();
		try (FileReader fr = new FileReader("src/resources/Webseiten/" + fileName)) {
			try (BufferedReader br = new BufferedReader(fr)) {
				while (true){
					String zeile = br.readLine();
					if (zeile == null) break;
					alleZeilen.append(zeile);
				}
			}
		}
		return alleZeilen.toString();
	}
	
	private static void loadWebPageBinary(String fileName, OutputStream os) throws FileNotFoundException, IOException
	{
		try (InputStream is = new FileInputStream("src/resources/Webseiten/" + fileName);
			 BufferedInputStream bis = new BufferedInputStream(is)) {
			byte buf[] = new byte[16384];
			while (true){
				int n = is.read(buf);
				if (n <= 0) break;
				os.write(buf, 0, n);
			}
		}
	}
}
