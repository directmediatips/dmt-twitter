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
import java.sql.SQLException;

import com.directmediatips.twitter.AbstractTwitterMachine;

import twitter4j.IDs;
import twitter4j.TwitterException;

/**
 * Automatically bans accounts that are blocked by this account.
 */
public class BanBlockedMachine extends AbstractTwitterMachine {

	/** SQL that sets the banned flag for an account. */
	public static final String BAN = "UPDATE accounts SET banned = 1"
			+ " WHERE id = ?";
	
	/** Prepared statement to ban an account. */
	protected PreparedStatement ban;
	
	/**
	 * Creates an BanBlockedMachine instance.
	 * @param account	a Twitter account screen name
	 * @throws IOException
	 * @throws SQLException
	 */
	public BanBlockedMachine(String account) throws IOException, SQLException {
		super(account);
		ban = connection.prepare(BAN);
	}

	/**
	 * Starts following accounts.
	 * @see com.directmediatips.twitter.AbstractTwitterMachine#go()
	 */
	@Override
	public void go() throws SQLException, TwitterException {
		IDs ids = twitter.getBlocksIDs();
		do {
    		for (long id : ids.getIDs()) {
    			ban.setLong(1, id);
   				ban.executeQuery();
   				System.out.println(String.format("Adding %s to the ban list", id));
   				twitter.destroyBlock(id);
    		}
    	} while (ids.hasNext());
	}
	
	/**
	 * Starts and runs the Twitter machine.
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println("An argument is required...");
			return;
		}
		System.out.println(String.format("Running BanBlockedMachine for %s...", args[0]));
		AbstractTwitterMachine app = null;
		try {
			app = new BanBlockedMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
