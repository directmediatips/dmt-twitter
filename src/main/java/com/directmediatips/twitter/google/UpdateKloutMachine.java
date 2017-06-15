package com.directmediatips.twitter.google;

import java.io.FileInputStream;

/*
 * Copyright 2017, Bruno Lowagie, Wil-Low BVBA
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the  * specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import com.directmediatips.google.sheets.klout.KloutMetrics;
import com.directmediatips.klout.Klout;
import com.directmediatips.klout.User;
import com.directmediatips.klout.UserId;

/**
 * Updates the Klout score in a Google sheets document.
 */
public class UpdateKloutMachine {

	/** The Klout instance. */
	protected Klout klout;
	
	/** The screen name of a Twitter account. */
	protected String account;

    /** The User id of the account. */
    protected UserId id;
	
	/**
	 * Creates an UpdateMetricsMachine instance.
	 *
	 * @param account the screen name of a Twitter account
	 * @throws SQLException the SQL exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public UpdateKloutMachine(String account) throws SQLException, IOException {
		this.account = account;
		Properties properties = new Properties();
		properties.load(new FileInputStream("klout/klout.properties"));
		klout = new Klout(properties.getProperty("apiKey"));
		id = new UserId(properties.getProperty(account));
	}
	
	/**
	 * Gets a Klout score from the Klout API and puts it in a Google Sheets
	 * document.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void go() throws IOException {
		User user = klout.getUser(id);
		KloutMetrics.UpdateMetrics(account, user.getScore());
	}
	
	/**
	 * Starts and runs the Twitter machine.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("An argument is required...");
			return;
		}
		System.out.println(String.format("Running UpdateKloutMachine for %s...", args[0]));
		UpdateKloutMachine app = null;
		try {
			app = new UpdateKloutMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

}
