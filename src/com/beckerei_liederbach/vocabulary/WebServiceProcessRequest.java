package com.beckerei_liederbach.vocabulary;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
		int         i2, i3, a, headersEnd = -1;
		byte        requestBody [] = new byte [1024*1024];
		InputStream is;
		try	{
			is = cs.getInputStream();
		}
		catch (IOException e) { e.printStackTrace(); return; }
		int nread;
		int requestBodyUsed = 0;
		while (headersEnd < 0 && requestBodyUsed < requestBody.length) {
			try	{
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
		String filePath = new String(requestBody, a, i - a);
		String reqParams = "";
		int qs = filePath.indexOf('?'); // extract request query string
		if (qs>=0) {
			reqParams = filePath.substring(qs+1);
			filePath = filePath.substring(0, qs);
		}
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
			if (name.equals("content-length")) contentLengthValue = Integer.parseInt(value);
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
		OutputStream os = null;
		try	{
			String user     = "";
			String pass     = "";
			String book     = "";
			String unit     = "";
			String question = "";
			String answer   = "";
			for (String nameValue : reqParams.split("&", -1)) {
				String nameValueSplit[] = nameValue.split("=", -1); 
				String name = nameValueSplit[0];
				String value = nameValueSplit.length > 1 ? URLDecoder.decode(nameValueSplit[1], "UTF-8") : "";
				if (name.equals("book") || name.equals("unit")) {
					if (value.contains("\\") || value.startsWith("/") || value.contains(":")) {
						throw new Exception("No paths allowed in book or unit");
					}
				}
				if      (name.equals("user"    )) user     = value;
				else if (name.equals("pass"    )) pass     = value;
				else if (name.equals("book"    )) book     = value;
				else if (name.equals("unit"    )) unit     = value;
				else if (name.equals("question")) question = value;
				else if (name.equals("answer"  )) answer   = value;
			}
			// System.out.println("Body: ***\r\n" + new String(requestBody, 0, requestBodyUsed) + "***\r\n");
			try	{
				os = cs.getOutputStream();
			}
			catch (IOException e) { System.out.println("error from getOutputStream: " + e.getMessage()); return; }
			outBuf.append("HTTP/1.0 200 OK\r\n" + "Content-Type: text/html; charset=UTF-8\r\n" + "\r\n");

			System.out.println("## " + filePath + " ## " + reqParams);
			if (filePath.equals("/favicon.ico")) {
				outBuf.append("have no /favicon.ico");
			} else if (filePath.equals("/question")) {
				String webPage = loadWebPage("next_question.html");
				String ratingAndNextQuestion[] = new String[2]; // die beiden Array Elemente werden von der Methode "evaluateAnswerAndGetNextQuestion" gefuellt
				String books = "<option value=\"" + book + "\">" + book + "</option>"; // the currently selected one
				String units = "<option value=\"" + unit + "\">" + unit + "</option>"; // the currently selected one
				try {
					List<String> books1 = webService.vokabelGameHaupt.listBooks();
					for (String b : books1) books += "<option value=\"" + b + "\">" + b + "</option>\r\n";
					List<String> units1 = webService.vokabelGameHaupt.listUnits(book);
					for (String u : units1) units += "<option value=\"" + u + "\">" + u + "</option>\r\n";
					webService.vokabelGameHaupt.evaluateAnswerAndGetNextQuestion(user, pass, book, unit, question, answer, ratingAndNextQuestion);
				}
				catch (Exception e) {
					ratingAndNextQuestion[0] = e.getMessage();
					ratingAndNextQuestion[1] = "";
				}
				webPage = webPage.replaceAll("\\$\\{user\\}"    , user);
				webPage = webPage.replaceAll("\\$\\{pass\\}"    , pass);
				webPage = webPage.replaceAll("\\$\\{book\\}"    , books);
				webPage = webPage.replaceAll("\\$\\{unit\\}"    , units);
				webPage = webPage.replaceAll("\\$\\{rating\\}"  , ratingAndNextQuestion[0]);
				webPage = webPage.replaceAll("\\$\\{question\\}", ratingAndNextQuestion[1]);
				outBuf.append(webPage);
			} else {
				outBuf.append("undefined file path");
			}
		}
		catch (Throwable e)	{
			e.printStackTrace();
			try	{
				outBuf.append(e.getMessage());
			}
			catch (Throwable e1) { e1.printStackTrace(); }
		}
		try {
			cs.setSoLinger(true, 60); // wait 60 seconds on close that browser can read the data
			if (os != null) {
				os.write(outBuf.toString().getBytes("UTF-8"));
				os.close();
			}
		} catch (IOException e) {
			System.out.println("error from setSoLinger/close: " + e.getMessage() + "\r\n");
		}
		try {
			cs.close();
		} catch (IOException e) {}
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
}
