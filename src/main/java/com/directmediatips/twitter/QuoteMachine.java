package com.directmediatips.twitter;

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
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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

import java.sql.SQLException;

import twitter4j.TwitterException;

/**
 * Creates tweets based on pre-canned quotes stored in a database.
 */
public class QuoteMachine extends AbstractTwitterMachine {

	/** SQL statement to get a quote from the database. */
	public static final String QUOTES = "SELECT id, quote, author FROM %s_quotes WHERE status = 0 LIMIT 0, 1";
	/** SQL statement to reset the status of all the quotes. */
	public static final String RESET = "UPDATE %s_quotes SET status = 0;";
	/** SQL statement to set the status of a single quote to 1. */
	public static final String USE = "UPDATE %s_quotes SET status = 1 WHERE id = ?";

	/** Prepared statement to mark a quote as used. */
	protected PreparedStatement setUsed;
	
	/**
	 * Creates a QuoteMachine instance.
	 * @param account	the screen name of a Twitter account
	 * @throws SQLException
	 * @throws IOException
	 */
	public QuoteMachine(String account) throws SQLException, IOException {
		super(account);
		setUsed = connection.prepare(String.format(USE, account));
	}
	
	/**
	 * Gets a single quote from the database, and tweets it.
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		ResultSet rs = connection.execute(String.format(QUOTES, account));
		if (!rs.next()) {
			System.out.println("Resetting the status of all quotes.");
			connection.execute(String.format(RESET, account));
		}
		rs = connection.execute(String.format(QUOTES, account));
		if (rs.next()) {
			setUsed.setInt(1, rs.getInt("id"));
			setUsed.execute();
			String status = String.format("\"%s\" - %s", rs.getString("quote"), rs.getString("author"));
			twitter.updateStatus(status);
			System.out.println(String.format("Tweeted: %s", status));
		}
	}
	
	/**
	 * Starts and runs the Twitter machine.
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("An argument is required...");
			return;
		}
		System.out.println(String.format("Running QuoteMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new QuoteMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}

}
