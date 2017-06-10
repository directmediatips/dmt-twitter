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

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Random;

import com.directmediatips.database.AbstractDatabaseMachine;

import twitter4j.RateLimitStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Superclass for all the machines that need access to a Twitter object,
 * as well as to a database.
 */
public abstract class AbstractTwitterMachine extends AbstractDatabaseMachine {
	
    /** Randomizer */
	public static final Random RANDOM = new Random();

	/** Our twitter4j instance. */
	protected Twitter twitter;
	/** The screen name of a Twitter account. */
	protected String account;
	/** The account properties. */
	protected Properties properties;
	/** Counts how many times we've performed a Twitter request. */
	protected int count = 0;
	
	/**
	 * Initializes the Twitter client.
	 * @param account	the screen name of a Twitter account
	 * @throws IOException 
	 * @throws SQLException
	 */
	public AbstractTwitterMachine(String account) throws IOException, SQLException {
		super();
		this.account = account;
		properties = new Properties();
		properties.load(new FileInputStream(String.format("twitter/%s.properties", account)));
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled("true".equals(properties.getProperty("debug")))
		  .setOAuthConsumerKey(properties.getProperty("oauth.consumerKey"))
		  .setOAuthConsumerSecret(properties.getProperty("oauth.consumerSecret"))
		  .setOAuthAccessToken(properties.getProperty("oauth.accessToken"))
		  .setOAuthAccessTokenSecret(properties.getProperty("oauth.accessTokenSecret"));
		TwitterFactory tf = new TwitterFactory(cb.build());
		twitter = tf.getInstance();
	}
	
	/**
	 * Executes whatever needs to be executed.
	 */
	public abstract void go() throws SQLException, TwitterException;

	/**
	 * Check the counter to find out if we're in danger of surpassing
	 * the Twitter limits for getting user information; if so, sleep for
	 * about five minutes.
	 */
	public void needsSleep(int maxCount) {
		if (count++ > maxCount) {
			count = 0;
	    	sleepRandom(300, 30);
		}
	}
	
	/**
	 * Checks if a TwitterException can be safely ignored.
	 * @param	code	the code of the TwitterException
	 * @return	true if the exception can be ignored
	 */
	public boolean isBreakingError(TwitterException e) {
		String msg = e.getErrorMessage();
		if (msg.contains("You are unable to follow more people at this time.")) {
			return true;
		}
		if (msg.contains("To protect our users from spam and other malicious activity, this account is temporarily locked.")) {
			return true;
		}
		if (msg.contains("User has been suspended")) {
			return false;
		}
		if (msg.contains("You can't follow yourself")) {
			return false;
		}
		if (msg.contains("User must be age screened")) {
			return false;
		}
		if (msg.contains("You've already requested to follow")) {
			return false;
		}
		RateLimitStatus rls = e.getRateLimitStatus();
		if (rls != null && rls.getRemaining() == 0) {
			System.out.println("Rate Limit Status exceeded");
			return true;
		}
		return true;
	}
	
	/**
	 * Checks if a TwitterException can be safely ignored.
	 */
	public void showErrorIfNecessary(TwitterException e) {
		if (isBreakingError(e))
			e.printStackTrace();
	}
	
	/**
	 * Sleep a random number of seconds
	 * @param	minimum	a minimum number of seconds
	 * @param	extra	a maximum of extra seconds
	 */
	public void sleepRandom(int minimum, int extra) {
		int seconds = minimum + RANDOM.nextInt(extra);
		System.out.println(String.format("Will sleep for %s seconds...", seconds));
		try {
			Thread.sleep(1000l * seconds);
    	} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
