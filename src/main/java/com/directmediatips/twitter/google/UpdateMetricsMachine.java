package com.directmediatips.twitter.google;

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

import com.directmediatips.google.sheets.twitter.TwitterMetrics;
import com.directmediatips.twitter.AbstractTwitterMachine;

import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Updates the Twitter metrics in a Google sheets document.
 */
public class UpdateMetricsMachine extends AbstractTwitterMachine {
	
	/**
	 * Creates an UpdateMetricsMachine instance.
	 * @param account	the screen name of a Twitter account
	 * @throws SQLException
	 * @throws IOException
	 */
	public UpdateMetricsMachine(String account) throws SQLException, IOException {
		super(account);
	}
	
	/**
	 * Gets a twitter user and updates the metrics of that user in a Google
	 * Sheets document.
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		User user = twitter.showUser(twitter.getId());
		System.out.println(twitter.getScreenName());
		try {
			TwitterMetrics.UpdateMetrics(
				user.getScreenName(),
				user.getStatusesCount(),
				user.getFriendsCount(),
				user.getFollowersCount(),
				user.getFavouritesCount());
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
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
		System.out.println(String.format("Running UpdateMetricsMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new UpdateMetricsMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}

}
