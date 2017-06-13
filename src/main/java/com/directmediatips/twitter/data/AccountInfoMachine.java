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

import twitter4j.IDs;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Looks at the friends and followers of your account, and harvests information
 * about those accounts.
 */
public class AccountInfoMachine extends AbstractTwitterMachine {
	
	// insert statements
	
	/** SQL inserting a record for an account. */
	public static final String CREATE_ACCOUNT = "INSERT INTO accounts"
			+ "(id, screenname, name, location, lang, description, url,"
			+ "followers, following, statuses, favorites, protected)"
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	/** SQL linking a record for an account. */
	public static final String LINK_ACCOUNT =
			"INSERT INTO %s_accounts (id) VALUES (?)";
	
	// we follow statements
	
	/** SQL Statement that updates the time stamp for every one we follow. */
	public static final String WE_FOLLOWED_ON =
			"UPDATE %s_accounts SET wefollowedon = CURRENT_TIMESTAMP WHERE wefollow = 1";
	/** SQL Statement that pretends that we aren't following any one anymore. */
	public static final String RESET_WE_FOLLOW = "UPDATE %s_accounts SET wefollow = 0";
	/** SQL Statement that registers our friends. */
	public static final String SET_WE_FOLLOW =
			"UPDATE %s_accounts SET wefollow = 1, wefollowedon = CURRENT_TIMESTAMP WHERE id = ?";
	/** SQL Statement that updates the date we started following someone. */
	public static final String SET_STARTFOLLOW =
			"UPDATE %s_accounts SET startfollow = wefollowedon WHERE startfollow IS NULL";
	
	// they follow statements
	
	/** SQL Statement that updates the time stamp for every one who follows us. */
	public static final String THEY_FOLLOWED_ON =
			"UPDATE %s_accounts SET theyfollowedon = CURRENT_TIMESTAMP WHERE theyfollow = 1";
	/** SQL Statement that pretends that no one is following us anymore. */
	public static final String RESET_THEY_FOLLOW = "UPDATE %s_accounts SET theyfollow = 0";
	/** SQL Statement that registers our followers. */
	public static final String SET_THEY_FOLLOW =
			"UPDATE %s_accounts SET theyfollow = 1, theyfollowedon = CURRENT_TIMESTAMP WHERE id = ?";
	
	// information statements
	
	/** SQL checking if an account record exists. */
	public static final String ACCOUNT_EXISTS =
		"SELECT count(*) FROM accounts WHERE id = ?";
	/** SQL checking if an account record is linked. */
	public static final String ACCOUNT_LINKED =
		"SELECT count(*) FROM %s_accounts WHERE id = ?";
	
	// Prepared statements

	/** Prepared statement to check if a record exists. */
	protected PreparedStatement exists;
    /** Prepared statement to insert a new account record. */
	protected PreparedStatement insert;
    /** Prepared statement to check if an account is linked. */
	protected PreparedStatement linked;
    /** Prepared statement to link an account. */
	protected PreparedStatement link;
	/** Prepared statement to set the wefollow flag. */
	protected PreparedStatement updateWeFollow;
	/** Prepared statement to set the theyfollow flag. */
	protected PreparedStatement updateTheyFollow;
	
	/**
	 * Creates an AccountInfoMachine instance.
	 * @param account	a Twitter account screen name
	 * @throws IOException
	 * @throws SQLException
	 */
	public AccountInfoMachine(String account) throws IOException, SQLException {
		super(account);
		exists = connection.prepare(ACCOUNT_EXISTS);
		insert = connection.prepare(CREATE_ACCOUNT);
		linked = connection.prepare(String.format(ACCOUNT_LINKED, account));
		link = connection.prepare(String.format(LINK_ACCOUNT, account));
		updateWeFollow = connection.prepare(String.format(SET_WE_FOLLOW, account));
		updateTheyFollow = connection.prepare(String.format(SET_THEY_FOLLOW, account));
	}

	/**
	 * Gets a list of friends and followers, and puts that information in a database.
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		listFriends();
		listFollowers();
	}
	
	/**
	 * Asks twitter for all the accounts we follow, and updates the
	 * database accordingly.
	 * @throws SQLException
	 * @throws TwitterException
	 */
	public void listFriends() throws SQLException, TwitterException {
    	IDs ids = twitter.getFriendsIDs(-1);
    	connection.execute(String.format(WE_FOLLOWED_ON, account));
    	connection.execute(String.format(RESET_WE_FOLLOW, account));
    	do {
    		for (long id : ids.getIDs()) {
   				insertAccount(id);
   				linkAccount(id);
				setWeFollow(id);
    		}
    	} while (ids.hasNext());
    	connection.execute(String.format(SET_STARTFOLLOW, account));
	}
	
	/**
	 * Asks twitter for all the accounts that follow us, and updates the
	 * database accordingly.
	 * @throws SQLException
	 * @throws TwitterException
	 */
	public void listFollowers() throws SQLException, TwitterException {
    	IDs ids = twitter.getFollowersIDs(-1);
    	connection.execute(String.format(THEY_FOLLOWED_ON, account));
    	connection.execute(String.format(RESET_THEY_FOLLOW, account));
    	do {
    		for (long id : ids.getIDs()) {
    			try {
    				insertAccount(id);
    				linkAccount(id);
    				setTheyFollow(id);
    			}
    			catch(TwitterException e) {
    				System.out.println(String.format("User %s caused exception: %s (%s).", id, e.getMessage(), e.getStatusCode()));
    				showErrorIfNecessary(e);
    			}
    		}
    	} while (ids.hasNext());
	}
	
	
	/**
	 * Creates an account for a Twitter profile in the database.
	 * @param id	the id of the Twitter profile.
	 * @throws SQLException
	 * @throws TwitterException
	 */
	public void insertAccount(long id) throws SQLException, TwitterException {
		if (exists(id)) return;
		User user = twitter.showUser(id);
		insert.setLong(1, user.getId());
		insert.setString(2, user.getScreenName());
		insert.setString(3, makeASCII(user.getName()));
		insert.setString(4, makeASCII(user.getLocation()));
		insert.setString(5, user.getLang());
		insert.setString(6, makeASCII(user.getDescription()));
		insert.setString(7, user.getURL());
		insert.setInt(8, user.getFollowersCount());
		insert.setInt(9, user.getFriendsCount());
		insert.setInt(10, user.getStatusesCount());
		insert.setInt(11, user.getFavouritesCount());
		insert.setString(12, user.isProtected() ? "Y" : "N");
		insert.executeUpdate();
		System.out.println(String.format("Account %s (%s) added.", id, user.getScreenName()));
		needsSleep(250);
	}
	
	/**
	 * Checks if an account with a specific id exists.
	 * @param	id	a Twitter id
	 * @return	true if an account with that id exists
	 * @throws SQLException 
	 */
	public boolean exists(long id) throws SQLException {
		exists.setLong(1, id);
		ResultSet rs = exists.executeQuery();
		if (rs.next()) {
			return (rs.getInt(1) == 1);
		}
		return false;
	}
	
	/**
	 * Links an account to our account.
	 * @param id	the id of the account we want to link.
	 * @throws SQLException
	 */
	public void linkAccount(long id) throws SQLException {
		if (isLinked(id)) return;
		link.setLong(1, id);
		link.executeUpdate();
		System.out.println(String.format("Account %s linked.", id));
	}
	
	/**
	 * Checks if an account with a specific id is linked to our account.
	 * @param	id	a Twitter id
	 * @return	true if an account with that id is linked to our account.
	 * @throws SQLException 
	 */
	public boolean isLinked(long id) throws SQLException {
		linked.setLong(1, id);
		ResultSet rs = linked.executeQuery();
		if (rs.next()) {
			return (rs.getInt(1) == 1);
		}
		return false;
	}
	
	/**
	 * Update a record for an account that we follow.
	 * @param id	the id of the account we want to link.
	 * @throws SQLException
	 */
	public void setWeFollow(long id) throws SQLException {
		updateWeFollow.setLong(1, id);
		updateWeFollow.executeUpdate();
	}
	
	/**
	 * Update a record for an account that we follow.
	 * @param id	the id of the account we want to link.
	 * @throws SQLException
	 */
	public void setTheyFollow(long id) throws SQLException {
		updateTheyFollow.setLong(1, id);
		updateTheyFollow.executeUpdate();
	}
	
	/**
	 * Starts and runs the Twitter machine.
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("An argument is required...");
			return;
		}
		System.out.println(String.format("Running AccountInfoMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new AccountInfoMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
