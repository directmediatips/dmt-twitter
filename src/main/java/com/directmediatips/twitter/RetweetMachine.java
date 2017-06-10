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
import java.util.Date;
import java.util.List;

import twitter4j.Status;
import twitter4j.TwitterException;

/**
 * Automatically retweets random tweets from selected accounts.
 */
public class RetweetMachine extends AbstractTwitterMachine {

	/** SQL to store the IDs that were retweeted for the current account. */
	public static final String RETWEET_ACCOUNTS =
			"SELECT id FROM %s_accounts WHERE retweet = 1";
	/** SQL to check if a tweet was already retweeted. */
	public static final String RETWEETED =
			"SELECT count(*) FROM retweets WHERE account = ? AND tweetid = ?";
	/** SQL to store the IDs that were retweeted for the current account. */
	public static final String RETWEET = "INSERT INTO retweets"
			+ " VALUES (?, ?);";

	/** A prepared statement to check if a tweet was already retweeted. */
	protected PreparedStatement retweeted;
	/** A prepared statement to insert a tweeted tweet. */
	protected PreparedStatement retweet;
	
	/** The maximum number of retweets. */
	protected int maximum;
	
	/**
	 * Creates a RetweetMachine instance.
	 * @param account	the screen name of a Twitter account
	 * @throws SQLException
	 * @throws IOException
	 */
	public RetweetMachine(String account) throws SQLException, IOException {
		super(account);
		try {
			maximum = Integer.parseInt(properties.getProperty("MaxRetweet"));
		}
		catch (Exception e) {
			maximum = 3;
		}
		retweeted = connection.prepare(RETWEETED);
		retweet = connection.prepare(RETWEET);
	}
	
	/**
	 * Gets a list of potential tweets, and retweets a random selection.
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		List<Status> list = new ArrayList<Status>();
		harvest(list);
		tweet(list);
	}
	
	/**
	 * Harvests a series of tweets from selected accounts.
	 * @param list	a list that will be populated with tweet IDs.
	 * @throws SQLException
	 * @throws TwitterException
	 */
	public void harvest(List<Status> list) throws SQLException, TwitterException {
		ResultSet rs = connection.execute(String.format(RETWEET_ACCOUNTS, account));
		while (rs.next()) {
			List<Status> statuses = twitter.getUserTimeline(rs.getLong("id"));
			for (Status status : statuses) {
				process(list, status);
			}
		}
	}
	
	/**
	 * Processes a status update.
	 * @param	status the Status to update
	 * @throws TwitterException 
	 */
	public void process(List<Status> list, Status status) throws SQLException {
		// Don't retweet if it might be a sensitive tweet
		if (status.isPossiblySensitive()) {
			return;
		}
		// Don't retweet retweets
		if (status.isRetweet()) {
			return;
		}
		// Don't retweet replies
		if (status.getInReplyToStatusId() > 0) {
			return;
		}
		// Don't retweet tweets older than 3 days
		if (new Date().getTime() - status.getCreatedAt().getTime() > 259200000l) {
			return;
		}
		// Don't re-retweet
		if (isRetweeted(status.getId())) {
			return;
		}
		list.add(status);
	}
	
	/**
	 * Checks if a tweet was already retweeted.
	 * @param id	the ID of the tweet that might be retweeted
	 * @return	true if the tweet was already retweeted
	 * @throws SQLException
	 */
	public boolean isRetweeted(long id) throws SQLException {
		retweeted.setString(1, account);
		retweeted.setLong(2, id);
		ResultSet rs = retweeted.executeQuery();
		if (rs.next()) {
			return rs.getInt(1) == 1;
		}
		return false;
	}
	
	/**
	 * Tweets a selection of tweets from a list.
	 * @param list	a list with tweet IDs
	 * @throws SQLException
	 * @throws TwitterException
	 */
	public void tweet(List<Status> list) throws SQLException, TwitterException {
		Collections.shuffle(list);
		int tweets = 0;
		String text;
		System.out.println(String.format("Retweeting %s tweets from %s statuses", maximum, list.size()));
		for (Status status: list) {
			text = status.getText();
			if (text.length() < 2) continue;
			if (text.charAt(0) == '@' || text.charAt(1) == '@')
				continue;
			retweet.setString(1, account);
			retweet.setLong(2, status.getId());
			retweet.executeQuery();
			twitter.retweetStatus(status.getId());
			System.out.println(String.format("Retweeted: %s", text));
			if (++tweets == maximum)
				break;
			sleepRandom(15, 45);
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
		System.out.println(String.format("Running RetweetMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new RetweetMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}

}
