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
 * Automatically unfriends banned accounts and accounts that didn't follow back
 * within 20 days.
 */
public class UnfriendMachine extends AbstractTwitterMachine {

	/** SQL to get follow information. */
	public static final String SELECT_BANNED =
		"SELECT a.id FROM accounts a, %s_accounts aa"
		+ " WHERE a.banned=1 AND aa.wefollow=1 AND a.id = aa.id";
	
	/** The Constant SELECT_UNFOLLOW. */
	public static final String SELECT_UNFOLLOW =
		"SELECT id FROM %s_accounts"
		+ " WHERE wefollow=1 AND theyfollow=0"
		+ " AND startfollow < DATE_SUB(NOW(), INTERVAL %s DAY)";
	
	/** The number of days we wait before unfollowing someone. */
	protected int waitDays;
	
	/**
	 * Creates an UnfriendMachine instance.
	 *
	 * @param account a Twitter account screen name
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	public UnfriendMachine(String account) throws IOException, SQLException {
		super(account);
		try {
			waitDays = Integer.parseInt(properties.getProperty("WaitDays"));
		}
		catch (Exception e) {
			waitDays = 20;
		}
	}

	/**
	 * Starts following accounts.
	 *
	 * @throws SQLException the SQL exception
	 * @throws TwitterException the twitter exception
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		unfriendBanned();
		unfriendUninterested();
	}
	
	/**
	 * Unfriends every account that was banned.
	 *
	 * @throws SQLException the SQL exception
	 */
	public void unfriendBanned() throws SQLException {
		ResultSet rs = connection.execute(String.format(SELECT_BANNED, account));
		while (rs.next()) {
			try {
				twitter.destroyFriendship(rs.getLong("id"));
				System.out.println(String.format("Unfriended banned account %s", rs.getLong("id")));
			} catch (TwitterException e) {
				showErrorIfNecessary(e);
			}
		}
	}
	
	/**
	 * Unfriends every account that didn't follow us back 20 days after the account was created.
	 *
	 * @throws SQLException the SQL exception
	 */
	public void unfriendUninterested() throws SQLException {
		ResultSet rs = connection.execute(String.format(SELECT_UNFOLLOW, account, waitDays));
		while (rs.next()) {
			try {
				twitter.destroyFriendship(rs.getLong("id"));
				System.out.println(String.format("Unfriended account %s because it didn't follow back within %s", rs.getLong("id"), waitDays));
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
		System.out.println(String.format("Running UnfriendMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new UnfriendMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
