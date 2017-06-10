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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.directmediatips.database.AbstractDatabaseMachine;
import com.directmediatips.google.sheets.twitter.TwitterRichData;

/**
 * Loops over different criteria, and matches accounts to those criteria.
 */
public class SelectDataMachine extends AbstractDatabaseMachine {

	/** Template for the SQL statement get a list of accounts of interest. */
	public static final String SELECT =
			"SELECT a.id, a.screenname FROM accounts a, %s_accounts aa WHERE aa.theyfollow = 1 AND a.id = aa.id AND (%s)";
	
	/**
	 * Creates an SelectDataMachine instance.
	 * @param account	a Twitter account screen name
	 * @throws IOException
	 * @throws SQLException
	 */
	public SelectDataMachine() throws IOException, SQLException {
		super();
	}
	
	/**
	 * Loops over the criteria, and tries to match accounts to those criteria.
	 * @throws SQLException
	 * @throws IOException 
	 */
	public void go() throws SQLException, IOException {
		TwitterRichData richData = new TwitterRichData();
		String where = richData.getWhereClause();
		List<Object> accounts = richData.getFromAccounts();
		ResultSet rs;
		int count = 0;
		for (Object account : accounts) {
			rs = connection.execute(String.format(SELECT, account, where));
			while (rs.next()) {
				richData.add(rs.getLong(1), rs.getString(2), count);	
			}
			count++;
		}
		richData.process(count);
	}
	
	/**
	 * Starts and runs the Twitter machine.
	 */
	public static void main(String[] args) {
		System.out.println("Running SelectDataMachine...");
		SelectDataMachine app = null;
		try {
			app = new SelectDataMachine();
			app.go();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (app != null)
			app.close();
	}
}
