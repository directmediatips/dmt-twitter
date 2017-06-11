package com.directmediatips.twitter.data;

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
import java.sql.SQLException;

import com.directmediatips.twitter.AbstractTwitterMachine;

import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Gets all the accounts that weren't banned from the database,
 * and updates their metrics.
 */
public class AccountUpdateMachine extends AbstractTwitterMachine {
	/** SQL to get ids from the accounts table */
	public static final String GET_ACCOUNTS =
			"SELECT id FROM accounts";
	
	/** SQL updating a record in the accounts table. */
	public static final String UPDATE_ACCOUNT = "UPDATE accounts"
			+ " SET screenname = ?, name = ?, location = ?, lang = ?,"
			+ " description = ?, url = ?, followers = ?, following = ?,"
			+ " statuses = ?, favorites = ?, protected = ?"
			+ " WHERE id = ? AND banned = 0";
	
	/** Prepared statement to update a record. */
	protected PreparedStatement update;
	
	/**
	 * Creates an AccountUpdateMachine instance.
	 * @param account	a Twitter account screen name
	 * @throws IOException
	 * @throws SQLException
	 */
	public AccountUpdateMachine(String account) throws IOException, SQLException {
		super(account);
		update = connection.prepare(UPDATE_ACCOUNT);
	}

	/**
	 * Gets a list of friends and followers, and puts that information in a database.
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		ResultSet rs = connection.execute(GET_ACCOUNTS);
		while (rs.next()) {
			try {
				updateAccount(rs.getLong(1));
			}
			catch(TwitterException e) {
				showErrorIfNecessary(e);	
			}
		}
	}
	
	/**
	 * Updates an account of a Twitter profile in the database.
	 * @param id	the id of the Twitter profile.
	 * @throws SQLException
	 * @throws TwitterException
	 */
	public void updateAccount(long id) throws SQLException, TwitterException {
		User user = twitter.showUser(id);
		update.setString(1, user.getScreenName());
		update.setString(2, makeASCII(user.getName()));
		update.setString(3, makeASCII(user.getLocation()));
		update.setString(4, user.getLang());
		update.setString(5, makeASCII(user.getDescription()));
		update.setString(6, user.getURL());
		update.setInt(7, user.getFollowersCount());
		update.setInt(8, user.getFriendsCount());
		update.setInt(9, user.getStatusesCount());
		update.setInt(10, user.getFavouritesCount());
		update.setString(11, user.isProtected() ? "Y" : "N");
		update.setLong(12, id);
		update.executeUpdate();
		System.out.println(String.format("Account %s (%s) updated.", id, user.getScreenName()));
		needsSleep(250);
	}
	
	/**
	 * Starts and runs the Twitter machine.
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("An argument is required...");
			return;
		}
		System.out.println(String.format("Running AccountUpdateMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new AccountUpdateMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
