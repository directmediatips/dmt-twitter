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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import twitter4j.IDs;
import twitter4j.TwitterException;

/**
 * Looks at the friends and followers of your account, and follows a selection of
 * those accounts.
 */
public class FollowMachine extends AbstractTwitterMachine {

	/** SQL to get follow information. */
	public static final String SELECT_FRIENDS_TO_FOLLOW =
		"SELECT id FROM %s_accounts WHERE followfriends = 1";
	/** SQL to get follow information. */
	public static final String SELECT_FOLLOWERS_TO_FOLLOW =
		"SELECT id FROM %s_accounts WHERE followfollowers = 1";
	/** SQL to get follow information. */
	public static final String ACCOUNT_DO_WE_FOLLOW =
		"SELECT wefollow FROM %s_accounts WHERE id = ?";
	/** SQL checking if an account is banned. */
	public static final String ACCOUNT_BANNED =
		"SELECT banned FROM accounts WHERE id = ?";

    /** Prepared statement to check if we follow an account. */
	protected PreparedStatement doWeFollow;
	/** Prepared statement to check if an account is banned. */
	protected PreparedStatement isBanned;
	
	/** The maximum number of accounts to follow in one go. */
	protected int maximum;
	
	/**
	 * Creates an FollowMachine instance.
	 * @param account	a Twitter account screen name
	 * @throws IOException
	 * @throws SQLException
	 */
	public FollowMachine(String account) throws IOException, SQLException {
		super(account);
		try {
			maximum = Integer.parseInt(properties.getProperty("MaxFollow"));
		}
		catch (Exception e) {
			maximum = 50;
		}
		doWeFollow = connection.prepare(String.format(ACCOUNT_DO_WE_FOLLOW, account));
		isBanned = connection.prepare(ACCOUNT_BANNED);
	}

	/**
	 * Starts following accounts.
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		List<Long> list = new ArrayList<Long>();
		harvestFromFriends(list);
		harvestFromFollowers(list);
		process(list);
	}
	
	/**
	 * Harvests friends of some other account.
	 * 
	 * @param list	the list to which we will add the accounts
	 * @throws SQLException
	 */
	public void harvestFromFriends(List<Long> list) throws SQLException {
		ResultSet rs = connection.execute(String.format(SELECT_FRIENDS_TO_FOLLOW, account));
		while (rs.next()) {
			try {
				IDs ids = twitter.getFriendsIDs(rs.getLong(1), -1);
				addToList(list, ids);
			} catch (TwitterException e) {
				showErrorIfNecessary(e);
			}
		}
	}
	
	/**
	 * Harvests friends of some other account.
	 * 
	 * @param list	the list to which we will add the accounts
	 * @throws SQLException
	 */
	public void harvestFromFollowers(List<Long> list) throws SQLException {
		ResultSet rs = connection.execute(String.format(SELECT_FOLLOWERS_TO_FOLLOW, account));
		while (rs.next()) {
			try {
				IDs ids = twitter.getFollowersIDs(rs.getLong(1), -1);
				addToList(list, ids);
			} catch (TwitterException e) {
				showErrorIfNecessary(e);
			}
		}
	}	
	
	/**
	 * Tries to add a series of IDs to the list of possible accounts to follow.
	 * @param list	the list that will be populated
	 * @param ids	a Twitter object consisting of IDs
	 * @throws SQLException
	 */
	public void addToList(List<Long> list, IDs ids) throws SQLException {
		for (long id : ids.getIDs()) {
			if (!doWeFollow(id) && !isBanned(id)) list.add(id);
		}
	}
	
	/**
	 * Checks if we already follow an account with this id.
	 * @param id	an account id
	 * @return	true if we already follow this account
	 * @throws SQLException
	 */
	public boolean doWeFollow(long id) throws SQLException {
		doWeFollow.setLong(1, id);
		ResultSet rs = doWeFollow.executeQuery();
		if (rs.next()) {
			return (rs.getInt(1) == 1);
		}
		return false;
	}
	
	/**
	 * Checks if an account is banned.
	 * @param	id	a Twitter id
	 * @return	true if an account with that id is banned
	 * @throws SQLException 
	 */
	public boolean isBanned(long id) throws SQLException {
		isBanned.setLong(1, id);
		ResultSet rs = isBanned.executeQuery();
		if (rs.next()) {
			return (rs.getInt(1) == 1);
		}
		return false;
	}
	
	/**
	 * Follows a random selection from the list of accounts that might be interesting to follow.
	 * @param list
	 * @throws SQLException 
	 */
	public void process(List<Long> list) throws SQLException {
		int followed = 0;
		Collections.shuffle(list);
		System.out.println(String.format("Picking %s accounts from %s candidates", maximum, list.size()));
		for (long id : list) {
			System.out.println(String.format("Examining %s (%s of %s)", id, followed + 1, maximum));
			try {
				if (!isBanned(id)) {
					twitter.createFriendship(id);
					if (++followed == maximum)
						break;
					sleepRandom(45, 15);
				}
			}
			catch(TwitterException e) {
				System.out.println(String.format("User %s caused exception: %s (%s)", id, e.getMessage(), e.getStatusCode()));
				if (e.exceededRateLimitation() || isBreakingError(e))
					break;
			}
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
		System.out.println(String.format("Running FollowMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new FollowMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
