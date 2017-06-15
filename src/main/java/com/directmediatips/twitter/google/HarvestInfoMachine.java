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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.directmediatips.google.sheets.twitter.TwitterHarvest;
import com.directmediatips.google.sheets.twitter.TwitterHarvest.Account;
import com.directmediatips.twitter.AbstractTwitterMachine;
import com.directmediatips.twitter.data.AccountInfoMachine;

import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Reads and writes accounts to harvest from from a Google Sheets document.
 */
public class HarvestInfoMachine extends AbstractTwitterMachine {
	
	/** The account for which we are going to update the harvest info. */
	protected String account;
	
	/** SQL statement to get the harvest info as stored in the database. */
	public static final String ACCOUNTS = "SELECT a.id, a.screenname,"
			+ " aa.followfriends, aa.followFollowers, aa.retweet"
			+ " FROM accounts a, %s_accounts aa WHERE a.id = aa.id"
			+ " AND (aa.followFriends > 0 OR aa.followFollowers > 0 OR aa.retweet > 0)";
	/** SQL statement to update harvest info for a specific account. */
	public static final String UPDATE = "UPDATE %s_accounts SET"
			+ " followfriends = ?, followfollowers = ?, retweet = ?"
			+ " WHERE id = ?";
	/** SQL statement to insert harvest info for a specific account. */
	public static final String INSERT = "INSERT INTO %s_accounts"
			+ " (id, followfriends, followfollowers, retweet)"
			+ " VALUES (?, ?, ?, ?)";

	/** The prepared statement to update harvest info. */
	protected PreparedStatement update;
	/** The prepared statement to insert harvest info. */
	protected PreparedStatement insert;
	/** The prepared statement to insert an account. */
	protected PreparedStatement insert_account;
	
	/** Keeps track of changes. */
	protected boolean changed = false;
	
	/**
	 * Creates a HarvestInfoMachine instance.
	 *
	 * @param account a Twitter account screen name
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException the SQL exception
	 */
	public HarvestInfoMachine(String account) throws IOException, SQLException {
		super(account);
		this.account = account;
		update = connection.prepare(String.format(UPDATE, account));
		insert = connection.prepare(String.format(INSERT, account));
		insert_account = connection.prepare(AccountInfoMachine.CREATE_ACCOUNT);
	}
	
	/**
	 * Gets the harvest info from a Google spreadsheets and compares
	 * it with what is stored in the database; adapts if necessary.
	 *
	 * @throws SQLException the SQL exception
	 * @throws TwitterException the twitter exception
	 */
	public void go() throws SQLException, TwitterException {
		try {
			// Get the data from a Google spreadsheet
			TwitterHarvest google = new TwitterHarvest(account);
			Map<Long, Account> map = google.getHarvestData();
			int count = map.size();
			Account harvest;
			// Get the data from the database
			ResultSet rs = connection.execute(String.format(ACCOUNTS, account));
			while (rs.next()) {
				harvest = map.remove(rs.getLong(1));
				if (harvest == null ||
				    harvest.isUnchanged(rs.getInt(3), rs.getInt(4), rs.getInt(5)))
					continue;
				// Update database if changes are detected
				update(rs.getLong(1), harvest);
			}
			// Check if harvest accounts need to be added
			for (Account entry : map.values()) {
				System.out.println(String.format("Looking up %s", entry.screenname));
				User user = twitter.showUser(entry.screenname);
				update(user.getId(), entry);
			}
			// If anything was changed, we need to upload the changes
			if (changed) {
				// Create a two-dimensional array with the database data
				rs = connection.execute(String.format(ACCOUNTS, account));
				List<List<Object>> data = new ArrayList<List<Object>>();
				while (rs.next()) {
					List<Object> row = new ArrayList<Object>();
					row.add(String.valueOf(rs.getLong(1)));
					row.add(rs.getString(2));
					row.add(rs.getInt(3));
					row.add(rs.getInt(4));
					row.add(rs.getInt(5));
					data.add(row);
					count--;
				}
				// Add empty rows in case we removed data
				List<Object> row = new ArrayList<Object>();
				row.add("");
				row.add("");
				row.add("");
				row.add("");
				row.add("");
				while (count-- > 0) {
					data.add(row);
				}
				// Send the updates to the spreadsheet
				google.update(data);
			}
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	/**
	 * Updates harvest data in the database.
	 *
	 * @param id the account id of the harvest account
	 * @param harvest the harvest data
	 * @throws SQLException the SQL exception
	 */
	protected void update(long id, Account harvest) throws SQLException {
		System.out.println(String.format("Updating info for %s (%s)", id, harvest.screenname));
		update.setInt(1, harvest.followFriends);
		update.setInt(2, harvest.followFollowers);
		update.setInt(3, harvest.retweet);
		update.setLong(4, id);
		int u = update.executeUpdate();
		if (u == 0) {
			System.out.println(String.format("Inserting info for %s (%s)", id, harvest.screenname));
			insert.setLong(1, id);
			insert.setInt(2, harvest.followFriends);
			insert.setInt(3, harvest.followFollowers);
			insert.setInt(4, harvest.retweet);
			insert.executeQuery();
			try {
				User user = twitter.showUser(id);
				insert_account.setLong(1, user.getId());
				insert_account.setString(2, user.getScreenName());
				insert_account.setString(3, makeASCII(user.getName()));
				insert_account.setString(4, makeASCII(user.getLocation()));
				insert_account.setString(5, user.getLang());
				insert_account.setString(6, makeASCII(user.getDescription()));
				insert_account.setString(7, user.getURL());
				insert_account.setInt(8, user.getFollowersCount());
				insert_account.setInt(9, user.getFriendsCount());
				insert_account.setInt(10, user.getStatusesCount());
				insert_account.setInt(11, user.getFavouritesCount());
				insert_account.setString(12, user.isProtected() ? "Y" : "N");
				insert_account.executeUpdate();
			}
			catch(SQLException e) {
				e.printStackTrace();
			} catch (TwitterException e) {
				e.printStackTrace();
			}
		}
		changed = true;
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
		System.out.println(String.format("Running HarvestInfoMachine for %s...", args[0]));
		HarvestInfoMachine app = null;
		try {
			app = new HarvestInfoMachine(args[0]);
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
