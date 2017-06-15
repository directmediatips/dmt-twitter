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
import java.sql.ResultSet;
import java.sql.SQLException;

import twitter4j.TwitterException;

/**
 * Follows back all the account that follow you, and that aren't banned or protected.
 */
public class FollowBackMachine extends AbstractTwitterMachine {

	/** SQL statement to find out who follows us that we didn't follow back yet. */
	public static final String FOLLOW_BACK = "SELECT aa.id, a.screenname"
			+ " FROM %s_accounts aa, accounts a "
			+ " WHERE aa.theyfollow = 1 AND aa.wefollow = 0"
			+ " AND aa.wefollowedon IS NULL AND aa.id = a.id"
			+ " AND a.banned = 0 AND a.protected = 'N'";
	
	/**
	 * Creates an FollowBackMachine instance.
	 *
	 * @param account a Twitter account screen name
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	public FollowBackMachine(String account) throws IOException, SQLException {
		super(account);
	}

	/**
	 * Starts following accounts that follow us.
	 *
	 * @throws SQLException the SQL exception
	 * @throws TwitterException the twitter exception
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		ResultSet rs = connection.execute(String.format(FOLLOW_BACK, account));
		while (rs.next()) {
			try {
				twitter.createFriendship(rs.getLong("id"));
				System.out.println(String.format("Following %s (%s)", rs.getLong("id"), rs.getString("screenname")));
				sleepRandom(15, 15);
			}
			catch (TwitterException e) {
				System.out.println(e.getMessage());
				if (isBreakingError(e))
					break;
			}
		}
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
		System.out.println(String.format("Running FollowBackMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new FollowBackMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
