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

import java.io.IOException;
import java.sql.SQLException;

import com.directmediatips.twitter.AbstractTwitterMachine;

import twitter4j.DirectMessage;
import twitter4j.ResponseList;
import twitter4j.TwitterException;
import twitter4j.api.DirectMessagesResources;

/**
 * Automatically removes all DMs.
 */
public class RemoveDMMachine extends AbstractTwitterMachine {
	
	/**
	 * Creates an UnfriendMachine instance.
	 *
	 * @param account a Twitter account screen name
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	public RemoveDMMachine(String account) throws IOException, SQLException {
		super(account);
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
		DirectMessagesResources resources = twitter.directMessages();
		ResponseList<DirectMessage> list = resources.getDirectMessages();
		for (DirectMessage dm : list) {
			System.out.println("From: " + dm.getSenderScreenName());
			System.out.println("To: "+ dm.getRecipientScreenName());
			System.out.println(dm.getCreatedAt());
			System.out.println(dm.getText());
			System.out.println("-----------------");
			try {
				twitter.destroyDirectMessage(dm.getId());
			}
			catch(TwitterException e) {
				System.out.println(e.getMessage());
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
		System.out.println(String.format("Running RemoveDMMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new RemoveDMMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
