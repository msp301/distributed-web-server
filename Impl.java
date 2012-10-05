import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Impl extends UnicastRemoteObject implements Server
{
	DatabaseHandler database;
	Replicator replicator;
	boolean primary;
	String name;
	int load = 0;

	Impl( String name, boolean primary, DatabaseHandler database, Replicator replicator )
		throws RemoteException
	{
		this.primary = ( primary == true ) ? true : false;
		this.database = database;
		this.replicator = replicator;
		this.name = "//localhost:1099/" + name; //store this server's name
	}

	//---- Client interfacing methods ----

	//retrieve product list using a given search string
	public String[] getProductList( String search ) throws RemoteException
	{
		load++;
		Hashtable<String, ArrayList<String>> products = database.getLocationList(); //get entire product list
		String[] temp = new String[ products.size() ]; //temporary buffer to store search elements
		String search_LC = search.toLowerCase(); //standardise given search term
		int found = 0;

		//search for products matching given search term
		Enumeration<String> e = products.keys();
		for( int i=0; e.hasMoreElements(); i++ )
		{
			String product_name = e.nextElement();

			String product_LC = product_name.toLowerCase();
			if( ( product_LC.contains(search_LC) ) || search.equals("") )
			{
				temp[found] = product_name;
				found++;
			}
		}

		System.out.println("Sent product list");

		return temp;
	}

	//create new product
	public String createProduct(String name, String desc) throws RemoteException
	{
		load++;
		boolean success = true;
		String result = "";
		String[] failures = new String[2];

		Server primary = replicator.getPrimary();

		//check if this server is the primary
		if( !isPrimary() )
		{
			//server is a secondary, request product creation
			System.out.println( "createProduct: Server is not primary" );
			primary.createProduct( name, desc ); //get primary to add product
		}
		else
		{
			System.out.println( "createProduct: Server is primary" );

			int fail_count = 0;
			String[] candidate_servers = replicator.getCandidates(); //get servers to add product to
			for( String candidate_name : candidate_servers )
			{
				//skip null candidates, produced if enough servers are not on the network
				if( candidate_name == null ) continue;

				try
				{
					//connect to winning candidate servers and create the new product entry
					System.out.println( "createProduct: Adding product " + name + " to server " + candidate_name );
					Server server = (Server) Naming.lookup( candidate_name );

					server.addProductEntry( name, desc, candidate_name ); //add product to server
				}
				catch( Exception e )
				{
					System.out.println( "createProduct: Failed to add product to " + candidate_name + " because " + e );
					result = "Could not create product entry for " + name;
					failures[ fail_count ] = candidate_name; //add candidate to list of failed candidates
					fail_count++;
				}
			}

			for( String server_name : getServerList() )
			{
				try
				{
					System.out.println( "createProduct: Connecting to server: " + server_name );
					Server server = (Server) Naming.lookup( server_name );

					for( String candidate_name : candidate_servers )
					{
						//skip null candidates, produced if enough servers are not on the network
						if( candidate_name == null ) continue;

						if( candidate_name.equals( server.getName() ) ||
							( failures[0] != null && failures[0] == candidate_name ) ||
							( failures[1] != null && failures[1] == candidate_name ) )
						{
							/* do not reinform candidate of itself having the product and do not
							 * add stored locations for servers that failed to add the product */
							continue;
						}
						System.out.println( "createProduct: Informing " + server_name + " of product " +
							name + " stored at " + candidate_name );
						server.addStoredLocation( name, candidate_name ); //inform server of stored product location
					}
				}
				catch( Exception e )
				{
					System.out.println( "createProduct: Failed to inform " + server_name +
						" of new product " + name + " because: " + e );
					continue;
				}
			}
		}

		result = "Added new product: " + name;
		System.out.println( result );
		return result;
	}

	//remove a given product from the database
	public boolean removeProduct( String product_name ) throws RemoteException
	{
		load++;
		boolean success = true;

		Server primary = replicator.getPrimary();

		//check if this server is the primary
		if( !isPrimary() )
		{
			//server is a secondary, request product removal
			System.out.println( "removeProduct: Server is not primary" );
			primary.removeProduct( product_name ); //get primary to remove product
		}
		else
		{
			System.out.println( "removeProduct: Server is primary" );

			String[] servers = replicator.getServerList(); //get server list
			String[] locations = database.getStoredLocation( product_name ); //get locations of product

			for( String server_name : servers )
			{
				boolean removed = false;

				try
				{
					System.out.println( "removeProduct: Connecting to server: " + server_name );
					Server server = (Server) Naming.lookup( server_name );

					//remove product if it is stored on the server
					for( String location : locations )
					{
						if( server_name.equals( location ) )
						{
							success = server.removeProductEntry( product_name ); //delete product from server
							removed = true;
							break;
						}
					}

					if( !removed )
					{
						//product was not stored on this server, just inform this one that the product is being removed
						server.removeStoredLocation( product_name );
					}
				}
				catch( Exception e )
				{
					System.out.println( "removeProduct: Encountered error removing product " +
						product_name + " from " + server_name + ": " + e );
					continue;
				}
			}
		}

		return success;
	}

	//retrieve a product
	public String[] getProduct( String name ) throws RemoteException
	{
		load++;
		String[] temp = new String[2];
		Product product = null;
		Server server = null;

		String[] product_location = database.getStoredLocation( name ); //get location of requested product

		for( String location : product_location )
		{
			System.out.println( "getProduct: Looking for entry at location: " + location );

			try
			{
				server = (Server) Naming.lookup( location ); //connect to server
				System.out.println( "getProduct: Connected to server: " + server.getName() );
				product = server.getProductData( name ); //get the requested product from the server
			}
			catch( Exception e )
			{
				System.out.println( "getProduct: Could not get product " + name + " from " + location );
				continue; //try another location
			}

			if( product != null ) break; //if product has been retrieved, do not try other servers
		}

		if( product != null )
		{
			System.out.println( "getProduct: Product " + product.getName() + " retrieved" );
			temp[0] = product.getName();
			temp[1] = product.getDesc();
		}
		else
		{
			return null;
		}

		return temp;
	}

	//get all reviews for a product
	public String[][] getReview( String name ) throws RemoteException
	{
		Product product = null;
		Server server = null;
		String[][] review = new String[10][3];

		String[] product_location = database.getStoredLocation( name ); //get location of requested product

		for( String location : product_location )
		{
			System.out.println( "getReview: Looking for entry at location: " + location );

			try
			{
				server = (Server) Naming.lookup( location ); //connect to server
				System.out.println( "getReview: Connected to server: " + server.getName() );
				product = server.getProductData( name ); //get the requested product from the server
			}
			catch( Exception e )
			{
				System.out.println( "getReview: Could not get product " + name + " from " + location );
				continue; //try another location
			}

			if( product != null ) break; //if product has been retrieved, do not try other servers
		}

		if( product != null )
		{
			System.out.println( "getReview: Product " + product.getName() + " retrieved" );
			review = product.getRev(); //get product's review list
		}
		else
		{
			return null;
		}

		return review;
	}

	//create a new review for a product
	public boolean submitReview(String name, String user, String score, String review)
		throws RemoteException
	{
		load++;
		boolean success = true;

		Server primary = replicator.getPrimary();

		//check if this server is the primary
		if( !isPrimary() )
		{
			//server is a secondary, request product removal
			System.out.println( "submitReview: Server is not primary" );
			primary.submitReview( name, user, score, review ); //get primary to submit new product review
		}
		else
		{
			System.out.println( "submitReview: Server is primary" );

			String[] locations = database.getStoredLocation( name ); //get locations of product

			for( String server_name : locations )
			{
				try
				{
					System.out.println( "submitReview: Connecting to server: " + server_name );
					Server server = (Server) Naming.lookup( server_name );

					success = server.addProductReview( name, user, score, review ); //add product review to server
				}
				catch( Exception e )
				{
					System.out.println( "submitReview: Encountered error submitting product " +
						name + " from " + server_name + ": " + e );
					continue;
				}
			}
		}

		return success;
	}

	//remove product review
	public boolean removeReview( String name, String review_id ) throws RemoteException
	{
		load++;
		boolean success = true;

		Server primary = replicator.getPrimary();

		//check if this server is the primary
		if( !isPrimary() )
		{
			//server is a secondary, request product review removal
			System.out.println( "removeReview: Server is not primary" );
			primary.removeReview( name, review_id ); //get primary to remove product review
		}
		else
		{
			System.out.println( "removeReview: Server is primary" );

			String[] locations = database.getStoredLocation( name ); //get locations of product

			for( String server_name : locations )
			{
				try
				{
					System.out.println( "removeReview: Connecting to server: " + server_name );
					Server server = (Server) Naming.lookup( server_name );

					success = server.removeReviewEntry( name, review_id ); //remove product review to server
				}
				catch( Exception e )
				{
					System.out.println( "removeReview: Encountered error removing product " +
						name + " from " + server_name + ": " + e );
					continue;
				}
			}
		}

		return success;
	}

	//simple method for use to test the liveness of a connected server
	public boolean ping() throws RemoteException
	{
		return true;
	}

	public boolean checkUserDetails( String[] details ) throws RemoteException
	{
		load++;
		boolean success = true;
		Server server;
		String password = null;
		String[] user_location = database.getUserLocation( details[0] ); //get location of requested user

		for( String location : user_location )
		{
			System.out.println( "checkUserDetails: Looking for entry at location: " + location );

			try
			{
				server = (Server) Naming.lookup( location ); //connect to server
				System.out.println( "checkUserDetails: Connected to server: " + server.getName() );
				password = server.getUserDetail( details[0], "password", details[1] ); //get the user details from the server
			}
			catch( Exception e )
			{
				System.out.println( "checkUserDetails: Could not get product " + name + " from " + location );
				continue; //try another location
			}

			if( password != null ) break; //if product has been retrieved, do not try other servers
		}

		if( password.equals( details[1] ) )
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public boolean completeReg(String[] details) throws RemoteException
	{
		boolean success = true;
		String[] failures = new String[2];

		String name = details[0];
		String password = details[1];
		String email = details[2];

		Server primary = replicator.getPrimary();

		//check if this server is the primary
		if( !isPrimary() )
		{
			//server is a secondary, request product creation
			System.out.println( "completeReg: Server is not primary" );
			primary.completeReg( details ); //get primary to add user
		}
		else
		{
			System.out.println( "completeReg: Server is primary" );

			int fail_count = 0;
			String[] candidate_servers = replicator.getCandidates(); //get servers to add product to
			for( String candidate_name : candidate_servers )
			{
				//skip null candidates, produced if enough servers are not on the network
				if( candidate_name == null ) continue;

				try
				{
					//connect to winning candidate servers and create the new product entry
					System.out.println( "completeReg: Adding user " + name + " to server " + candidate_name );
					Server server = (Server) Naming.lookup( candidate_name );

					server.createUser( details, candidate_name ); //add user to server
				}
				catch( Exception e )
				{
					System.out.println( "completeReg: Failed to add user to " + candidate_name + " because " + e );
					failures[ fail_count ] = candidate_name; //add candidate to list of failed candidates
					fail_count++;
				}
			}

			for( String server_name : getServerList() )
			{
				try
				{
					System.out.println( "completeReg: Connecting to server: " + server_name );
					Server server = (Server) Naming.lookup( server_name );

					for( String candidate_name : candidate_servers )
					{
						//skip null candidates, produced if enough servers are not on the network
						if( candidate_name == null ) continue;

						if( candidate_name.equals( server.getName() ) ||
							( failures[0] != null && failures[0] == candidate_name ) ||
							( failures[1] != null && failures[1] == candidate_name ) )
						{
							/* do not reinform candidate of itself having the user account and do not
							 * add stored locations for servers that failed to add the user */
							continue;
						}
						System.out.println( "completeReg: Informing " + server_name + " of user " +
							name + " stored at " + candidate_name );
						server.addUserLocation( name, candidate_name ); //inform server of stored product location
					}
				}
				catch( Exception e )
				{
					System.out.println( "completeReg: Failed to inform " + server_name +
						" of new product " + name + " because: " + e );
					continue;
				}
			}
		}

		return success;
	}

	public boolean changeDetail( String user,String detail,String value )
		throws RemoteException
	{
		boolean success = true;
		Server primary = replicator.getPrimary();

		//check if this server is the primary
		if( !isPrimary() )
		{
			//server is a secondary, request user removal
			System.out.println( "changeDetail: Server is not primary" );
			primary.changeDetail( user, detail, value ); //get primary to change user details
		}
		else
		{
			System.out.println( "changeDetail: Server is primary" );

			String[] servers = replicator.getServerList(); //get server list
			String[] locations = database.getUserLocation( user ); //get locations of user

			for( String server_name : servers )
			{
				boolean removed = false;

				try
				{
					System.out.println( "changeDetail: Connecting to server: " + server_name );
					Server server = (Server) Naming.lookup( server_name );

					//set user details if it is stored on the server
					for( String location : locations )
					{
						if( server_name.equals( location ) )
						{
							success = server.setUserDetail( user, detail, value ); //set user details on server
							removed = true;
							break;
						}
					}
				}
				catch( Exception e )
				{
					System.out.println( "changeDetail: Encountered error setting details for user " +
						user + " on " + server_name + ": " + e );
					continue;
				}
			}
		}

		return success;
	}

	public boolean delUser(String user) throws RemoteException
	{
		boolean success = true;

		Server primary = replicator.getPrimary();

		//check if this server is the primary
		if( !isPrimary() )
		{
			//server is a secondary, request user removal
			System.out.println( "delUser: Server is not primary" );
			primary.delUser( user ); //get primary to remove user
		}
		else
		{
			System.out.println( "delUser: Server is primary" );

			String[] servers = replicator.getServerList(); //get server list
			String[] locations = database.getUserLocation( user ); //get locations of user

			for( String server_name : servers )
			{
				boolean removed = false;

				try
				{
					System.out.println( "delUser: Connecting to server: " + server_name );
					Server server = (Server) Naming.lookup( server_name );

					//remove user if it is stored on the server
					for( String location : locations )
					{
						if( server_name.equals( location ) )
						{
							success = server.deleteUser( user ); //delete user from server
							removed = true;
							break;
						}
					}

					if( !removed )
					{
						//user was not stored on this server, just inform this one that the user is being removed
						server.removeUserLocation( user );
					}
				}
				catch( Exception e )
				{
					System.out.println( "delUser: Encountered error removing user " +
						user + " from " + server_name + ": " + e );
					continue;
				}
			}
		}

		return success;
	}

	public void wipeDatabase() throws RemoteException
	{
		database.resetDatabase();
	}

	//---- Back-end server methods ----

	public String[] getServerList() throws RemoteException
	{
		String[] server_list = replicator.getServerList();

		return server_list;
	}

	//retrieve all product data
	public Product[] getProductData() throws RemoteException
	{
		Product[] products = database.getProducts();

		return products;
	}

	//retrieve a single products' data
	public Product getProductData( String name ) throws RemoteException
	{
		Product product = database.getProductEntry( name );

		return product;
	}

	//check whether review server is the primary
	public boolean isPrimary() throws RemoteException
	{
		return this.primary;
	}

	//retrieve the name of the server
	public String getName() throws RemoteException
	{
		return this.name;
	}

	//retrieve updated product location mapping
	public Hashtable<String, ArrayList<String>> getLocationList() throws RemoteException
	{
		return database.getLocationList();
	}

	//retrieve the server's election id used to determine a new primary
	public int getElectionId() throws RemoteException
	{
		return replicator.getElectionId();
	}

	//set server to either step up or step down as a primary
	public void changePrimary( String server_name ) throws RemoteException
	{
		replicator.changePrimary( server_name ); //update replicator service

		System.out.println( "changePrimary: Given primary = " + server_name + " - I am = " + getName() );
		if( server_name.equals( getName() ) )
		{
			this.primary = true; //set server as primary
		}
		else
		{
			this.primary = false; //demote to secondary
		}

		System.out.println( "changePrimary: Primary indicator is now = " + this.primary );
	}

	//---- Database control methods for back-end review server inter-communication ----

	//create a new product entry in the server's database
	public boolean addProductEntry( String name, String desc, String server_name ) throws RemoteException
	{
		load++;
		return database.addProductEntry( name, desc, server_name );
	}

	//remove a product entry from the server's database
	public boolean removeProductEntry( String name ) throws RemoteException
	{
		load++;
		return database.deleteProduct( name );
	}

	//add product review to the server's database
	public boolean addProductReview(
		String name,
		String user,
		String score,
		String review ) throws RemoteException
	{
		load++;
		return database.addProductReview( name, user, score, review );
	}

	//remove product review from the server's database
	public boolean removeReviewEntry( String name, String review_id ) throws RemoteException
	{
		load++;
		return database.removeReviewEntry( name, review_id );
	}

	//create a new stored server location for a product
	public void addStoredLocation( String product, String server_name ) throws RemoteException
	{
		load++;
		database.addStoredLocation( product, server_name );
	}

	//remove stored location of a product from server's database
	public void removeStoredLocation( String product, String server_name ) throws RemoteException
	{
		database.removeStoredLocation( product, server_name );
	}

	//remove all stored locations of a product from server's database
	public void removeStoredLocation( String product ) throws RemoteException
	{
		database.removeStoredLocation( product );
	}

	//retrieve list of servers storing this product entry
	public String[] getStoredLocation( String name ) throws RemoteException
	{
		load++;
		return database.getStoredLocation( name );
	}

	//----- User account accessor methods -----

	//add new user account entry into server's database
	public boolean createUser( String[] details, String server_name ) throws RemoteException
	{
		return database.createUser( details, server_name );
	}

	//remove user account entry from server's database
	public boolean deleteUser( String user ) throws RemoteException
	{
		return database.deleteUser( user );
	}

	public User[] getUserData() throws RemoteException
	{
		load++;
		User[] users = database.getUsers();

		return users;
	}

	public boolean setConfig(String name, String[] config) throws RemoteException
	{
		load++;
		boolean success = true;
		Server server;

		String[] user_location = database.getUserLocation( name ); //get location of requested user

		for( String location : user_location )
		{
			System.out.println( "setConfig: Looking for entry at location: " + location );

			try
			{
				server = (Server) Naming.lookup( location ); //connect to server
				System.out.println( "setConfig: Connected to server: " + server.getName() );
				server.setUserConfig( name, config ); //set user config on the server
			}
			catch( Exception e )
			{
				System.out.println( "setConfig: Could not get product " + name + " from " + location );
				continue; //try another location
			}
		}

		return success;
	}

	//update user account details stored on the server's database
	public boolean setUserDetail( String user, String detail, String value ) throws RemoteException
	{
		return database.setUserDetail( user, detail, value );
	}

	//retrieve user account details from the server's database
	public String getUserDetail( String user, String detail, String value ) throws RemoteException
	{
		return database.getUserDetail( user, detail, value );
	}

	//store user configuration details into the server's database
	public boolean setUserConfig( String name, String[] config ) throws RemoteException
	{
		return database.setUserConfig( name, config );
	}

	public String[] getConfig(String name) throws RemoteException
	{
		load++;

		Server server;
		String[] user_config = null;
		String[] user_location = database.getUserLocation( name ); //get location of requested user

		for( String location : user_location )
		{
			System.out.println( "getConfig: Looking for entry at location: " + location );

			try
			{
				server = (Server) Naming.lookup( location ); //connect to server
				System.out.println( "getConfig: Connected to server: " + server.getName() );
				user_config = server.getUserConfig( name ); //get the requested user configuration
			}
			catch( Exception e )
			{
				System.out.println( "getConfig: Could not get config for user " + name + " from " + location );
				continue; //try another location
			}

			if( user_config != null ) break; //if config has been retrieved, do not try other servers
		}

		return user_config;
	}

	//retrieve user config from server's database
	public String[] getUserConfig( String name ) throws RemoteException
	{
		return database.getUserConfig( name );
	}

	//add a new stored server location for a user account
	public void addUserLocation( String username, String server_name ) throws RemoteException
	{
		database.addUserLocation( username, server_name );
	}

	//remove stored location of a user account
	public void removeUserLocation( String username, String server_name ) throws RemoteException
	{
		database.removeStoredLocation( username, server_name );
	}

	//remove all stored locations of a user account
	public void removeUserLocation( String username ) throws RemoteException
	{
		database.removeStoredLocation( username );
	}

	//retrieve list of servers storing a user account
	public String[] getUserLocation( String username ) throws RemoteException
	{
		return database.getStoredLocation( username );
	}

	//----- Load balancing accessor methods -----

	public int getLoad() throws RemoteException
	{
		return load;
	}

	public void popLoadList() throws RemoteException
	{
		int l = 0;
		Server server;
		//if(primary)
		{
			try
			{
				for( String server_name : getServerList() )
				{
					server = (Server) Naming.lookup( server_name );
					l = server.getLoad();
					database.setLoadVal(new String[]{server_name,Integer.toString(l)});
					System.out.println(server_name+" is at "+l);
				}
			}
			catch (Exception e) {e.printStackTrace();}
			server = null;
		}
	}

	public ArrayList<String[]> getLoadList() throws RemoteException
	{
		return database.getLoad();
	}

	public String getLowestLoad() throws RemoteException
	{
		popLoadList();

		String lowest = "";
		int val = 0;
		ArrayList<String[]> list = database.getLoad();
		val = Integer.parseInt(list.get(0)[1])+1;
		for(int i = 0; i < list.size(); i++)
		{
			if(Integer.parseInt(list.get(i)[1]) < val)
			{
				lowest = list.get(i)[0];
				val = Integer.parseInt(list.get(i)[1]);
			}
		}
		System.out.println("Lowest Load Server is "+lowest+" with "+val);
		return lowest;
	}
	
	//add new server to the system
	public boolean addServer( String new_server ) throws RemoteException
	{
		boolean success = true;

		if( isPrimary() )
		{
			database.addServerEntry( new_server );
			
			for( String server_name : getServerList() )
			{
				if( server_name.equals( getName() ) ) continue;
				
				try
				{
					Server server = (Server) Naming.lookup( server_name );
					server.addServerEntry( new_server ); //add new server to server's list
				}
				catch( Exception e )
				{
					System.out.println( "addServer: Failed to inform " +
						server_name + " of " + new_server );
					success = false;
				}
			}
		}

		return success;
	}

	//inform server of a new server
	public boolean addServerEntry( String server_name ) throws RemoteException
	{
		return database.addServerEntry( server_name );
	}
}
