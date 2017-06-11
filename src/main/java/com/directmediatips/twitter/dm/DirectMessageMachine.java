package com.directmediatips.twitter.dm;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import com.directmediatips.google.sheets.twitter.TwitterRichData;
import com.directmediatips.google.sheets.twitter.TwitterRichData.Account;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Gets account info from Google sheets and sends DMs accordingly.
 */
public class DirectMessageMachine {

    /** Randomizer */
	protected Random rand = new Random();
	
	/**
	 * Creates an EnrichDataMachine instance.
	 * @param account	a Twitter account screen name
	 * @throws IOException
	 * @throws SQLException
	 */
	public DirectMessageMachine() throws IOException, SQLException {
		super();
	}
	
	/**
	 * Gets account info from Google sheets and sends DMs based on that info.
	 * @throws SQLException
	 * @throws IOException 
	 */
	public void go() throws SQLException, IOException {
		TwitterRichData richData = new TwitterRichData();
		// gets the message that needs to be sent
		String message = richData.getDirectMessage();
		// gets our accounts and instantiates a Twitter object for each account
		List<Object> accounts = richData.getFromAccounts();
		List<Twitter> twitter = new ArrayList<Twitter>();
		int count = 0;
		for (Object account : accounts) {
			twitter.add(getTwitterInstance(account.toString()));
			count++;
		}
		int dms[] = new int[count];
		// Gets accounts that will be sent a message
		Map<Long, Account> data = richData.getToAccounts();
		// Constructs a list for the updates
		List<List<Object>> updated = new ArrayList<List<Object>>();
		// Loop over the account data
		List<Object> row;
		boolean sent = false;
		Account account;
		for (Map.Entry<Long, Account> entry : data.entrySet()) {
			row = new ArrayList<Object>();
			row.add(entry.getKey().toString());
			account = entry.getValue();
			row.add(account.screenname);
			System.out.println(String.format("Trying to send message to %s", account.screenname));
			for (int i = 0; i < count; i++) {
				if (account.accounts.contains(i)) {
					if (sent || !sendDM(message, entry.getKey(), i, twitter, dms)) {
						// Not used to send a DM
						row.add("X");
					}
					else {
						// Used to send a DM
						row.add(new Date().toString());
						sent = true;
					}
				}
				else {
					// Not possible to send a DM
					row.add(0);
				}
			}
			sent = false;
			updated.add(row);
		}
		// Update the data in the spreadsheet
		richData.update(updated);
	}
	
	/**
	 * Try to send a direct message to an account with a specific id
	 * from one of the accounts from our list of accounts
	 * @param message	the message
	 * @param to	the id of the account we want to send a DM to
	 * @param from	the index of an account in the <code>accounts</code> list
	 * @param accounts	a list containing <code>Twitter</code> objects
	 * @param count	an array keeping track of the messages that have been sent
	 * @return	<code>true</code> if the DM was successfully sent
	 */
	public boolean sendDM(String message, long to, int from, List<Twitter> accounts, int[] count) {
		// We can only send 250 messages a day
		if (count[from]++ > 250) return false;
		// Try to send a DM
		try {
			accounts.get(from).sendDirectMessage(to, message);
			long r = 45000l + rand.nextInt(45000);
			System.out.println(String.format("Sleep for %s seconds", r / 1000));
			Thread.sleep(r);
			return true;
		} catch (TwitterException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Gets a Twitter instance for an account.
	 * @param account	the screen name of one of our accounts
	 * @return	a Twitter instance
	 * @throws IOException
	 */
	public Twitter getTwitterInstance(String account) throws IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(String.format("twitter/%s.properties", account)));
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled("true".equals(properties.getProperty("debug")))
		  .setOAuthConsumerKey(properties.getProperty("oauth.consumerKey"))
		  .setOAuthConsumerSecret(properties.getProperty("oauth.consumerSecret"))
		  .setOAuthAccessToken(properties.getProperty("oauth.accessToken"))
		  .setOAuthAccessTokenSecret(properties.getProperty("oauth.accessTokenSecret"));
		TwitterFactory tf = new TwitterFactory(cb.build());
		return tf.getInstance();
	}
	
	/**
	 * Starts and runs the Twitter machine.
	 */
	public static void main(String[] args) {
		System.out.println("Running DirectMessageMachine...");
		DirectMessageMachine app = null;
		try {
			app = new DirectMessageMachine();
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
