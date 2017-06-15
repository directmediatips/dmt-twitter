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
import java.util.Date;

import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Automatically unfriends accounts from people who haven't tweeted for a long time.
 */
public class RemoveInactiveMachine extends AbstractTwitterMachine {

	/** The Constant SELECT_UNFOLLOW. */
	public static final String SELECT_UNFOLLOW =
		"SELECT id FROM %s_accounts WHERE wefollow=1";

	/** The maximum number of accounts to follow in one go. */
	protected long inactivity;
	
	/**
	 * Creates a RemoveInactiveMachine instance.
	 *
	 * @param account a Twitter account screen name
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	public RemoveInactiveMachine(String account) throws IOException, SQLException {
		super(account);
		try {
			inactivity = 86400000l * Integer.parseInt(properties.getProperty("inactiveDays"));
		}
		catch (Exception e) {
			inactivity = 31556952000l;
		}
	}

	/**
	 * Starts examining accounts (and removing the inactive ones).
	 *
	 * @throws SQLException the SQL exception
	 * @throws TwitterException the twitter exception
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		ResultSet rs = connection.execute(String.format(SELECT_UNFOLLOW, account));
		long id;
		while (rs.next()) {
			try {
				id = rs.getLong(1);
				User user = twitter.showUser(id);
				Status status = user.getStatus();
				if (status != null) {
					Date date = status.getCreatedAt();
					Date now = new Date();
					if (now.getTime() - date.getTime() > inactivity) {
						twitter.destroyFriendship(id);
						System.out.println(String.format("Unfriending %s: inactive account", user.getScreenName()));
					}
				}
				this.needsSleep(250);
			} catch (TwitterException e) {
				showErrorIfNecessary(e);
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
		System.out.println(String.format("Running RemoveInactiveMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new RemoveInactiveMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
