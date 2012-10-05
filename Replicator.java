import java.rmi.*;
import java.rmi.registry.*;
import java.lang.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

public class Replicator implements Serializable
{
	boolean is_primary;
	DatabaseHandler database;
	Server remote_primary;
	Server temp_server = null;
	String server_name;
	int election_id;
	int replicated_data_entries = 2;
	ArrayList<String> temp = new ArrayList<String>();

	public Replicator( String server_name, String host, boolean primary, DatabaseHandler database )
	{
		this.server_name = "//"+host+":1099/" + server_name; //store own server name
		this.is_primary = primary; //set replicator service type
		this.database = database;

		createElectionId(); //create election id for this server

		try
		{
			restoreData( database.getStoredProducts() ); //restore product data from database file
		}
		catch( Exception e )
		{
			System.out.println( "Replicator: Could not restore data" );
		}
	}

	//connect to primary server or start election for a primary if on initial setup none exists
	public void connect()
	{
		String current_primary = findPrimary();

		//check if this is a secondary server and if so, connect to primary
		if( this.is_primary == false )
		{
			if( current_primary == null )
			{
				System.out.println( "connect: No current primary, starting election" );
				startElection(); //start election to elect a primary
			}
			else
			{
				rmiConnect( current_primary ); //connect to primary
			}

			//inform primary server of existance, so that server can be used as a resource
			try
			{
				getPrimary().addServerEntry( server_name ); //tell server that new server exists
			}
			catch( Exception e )
			{
				System.out.println( "connect: Failed to inform primary of existance because " + e );
			}

			//get latest product location list from primary
			try
			{
				database.setLocationList( getPrimary().getLocationList() );
			}
			catch( Exception e )
			{
				System.out.println( "connect: Could not retrieve product location list from primary because " + e );
			}
		}
		else
		{
			//server is set as primary, double check whether someone else is already elected
			if( current_primary != null )
			{
				try
				{
					Server self = (Server)Naming.lookup( this.server_name );
					self.changePrimary( current_primary ); //force myself to stand down
					database.setLocationList( getPrimary().getLocationList() ); //get latest location data from primary
				}
				catch( Exception e )
				{
					System.out.println( "connect: Failed to connect to current primary" );
					startElection(); //run election for primary
				}
			}
		}
	}

	//add given product data into server's database and reinstate them into the system
	public void restoreData( Product[] stored_products ) throws RemoteException
	{
		for( Product product : stored_products )
		{
			database.addProductEntry( product, this.server_name ); //add product to this server
			for( String server_name : getServerList() )
			{
				try
				{
					Server server = (Server) Naming.lookup( server_name );
					server.addStoredLocation( product.getName(), this.server_name ); //inform server of product's location
				}
				catch( Exception e )
				{
					System.out.println( "restoreData: Failed to inform " + server_name + ":" + e );
				}
			}
		}
	}

	//set replication service to either step up or step down as a primary
	public void changePrimary( String server_name )
	{
		System.out.println( "changePrimary: Requested to change primary server to: " + server_name );

		if( this.server_name.equals( server_name ) )
		{
			this.is_primary = true;
		}
		else
		{
			this.is_primary = false;
		}

		System.out.println( "changePrimary: Primary replicator set to " + this.is_primary );

		//if no longer a primary, connect to given primary
		if( !this.is_primary )
		{
			System.out.println( "changePrimary: About to connect to new primary: " + server_name );
			rmiConnect( server_name );
		}
		else
		{
			setPrimary( null ); //erase remote primary cache as this is a primary server
		}
	}

	//set the name of the primary server
	private void setPrimary( Server server )
	{
		System.out.println( "setPrimary: About to update primary server" );
		this.remote_primary = server;
	}

	//abstration method to setup RMI connection to server
	private boolean rmiConnect( String server_name )
	{
		boolean success = true;

		if( server_name != null )
		{
			try
			{
				//connect to primary server
				setPrimary( (Server)Naming.lookup( server_name ) );
			}
			catch (Exception e)
			{
				System.out.println( "rmiConnect: Caught an exception doing name lookup on " + server_name + ": " + e );
				success = false;
			}
		}
		else
		{
			success = false;
		}

		return success;
	}

	private boolean rmiTest( String server_name )
	{
		if( server_name != null )
		{
			try
			{
				Server temp_server = (Server)Naming.lookup( server_name );

				//Pattern pattern = Pattern.compile( "(//.+)/" );
				//Matcher matcher = pattern.matcher( server_name );

				//Registry reg = LocateRegistry.getRegistry( matcher.group( 1 ) );
				//Registry reg = LocateRegistry.getRegistry( "192.168.0.20" );
				//System.out.println( "rmiTest: Retrieved registry" );
				//Server temp_server = (Server) reg.lookup( "//localhost/meme" );
				temp_server.ping();
				return true;
			}
			catch (Exception e)
			{
				System.out.println( "rmiTest: Failed to connect to " +
					server_name + " because " + e );
				return false;
			}
		}

		return false;
	}

	//locate and retrieve the primary server's name
	private String findPrimary()
	{
		String[] server_list = null;
		String primary_server = null;
		Server server = null;

		try
		{
			server_list = getServerList(); //find servers

			int counter = 0;
			for( String item : server_list )
			{
				System.out.println( "Item " + counter + " = " + item );
				counter++;
			}

			//search for primary by trying servers from server list
			for( String server_name : server_list )
			{
				if( !server_name.equals( this.server_name ) )
				{
					System.out.println( "findPrimary: Found " + server_name );

					try
					{
						server = (Server)Naming.lookup( server_name ); //attempt connection

						if( server.isPrimary() )
						{
							//primary server found, store the primary's name
							primary_server = server_name;
							break;
						}
					}
					catch( ConnectException e )
					{
						//server is dead, exit current loop iteration and try another server
						System.out.println( "findPrimary: Failed to connect to " + server_name );
						continue;
					}
				}
			}
		}
		catch( Exception e )
		{
			System.out.println( "findPrimary: Caught an exception finding primary server - " + e );
		}

		System.out.println( "findPrimary: Returning primary server name as: " + primary_server );
		return primary_server;
	}

	//retrive latest database information
	public boolean updateData()
	{
		boolean success = true;

		try
		{
			//check server is actually a secondary server
			if( !this.is_primary )
			{
				System.out.println( "updateData: Getting data from: " + remote_primary.getName() );
				Product[] products = remote_primary.getProductData(); //get latest products

				for( Product product : products )
				{
					System.out.println( "updateData: Found product = '" + product + "'" );
				}

				database.resetDatabase(); //clear local database, ready for update

				//add products to database
				for( Product product : products )
				{
					if( product == null ) continue; //do not attempt to add null products
					//database.addProductEntry( product.getName(), product.getDesc() );

					//add any product reviews for the product
					for( String[] review : product.getRev() )
					{
						//do not attempt to add null reviews
						if( review[0] != null && review[1] != null && review[2] != null )
						{
							database.addProductReview(
								product.getName(),
								review[0],
								review[1],
								review[2]
							);
						}
					}
				}
			}
		}
		catch( Exception e )
		{
			System.out.println( "updateData: Could not get data from remote: " + e );
			success = false;
		}

		return success;
	}

	//retrieve 2 candidate servers for adding new product entries, used for scaling data across system
	public String[] getCandidates()
	{
		String[] candidates = new String[2];

		String[] server_list = getServerList();
		int local_data_no = database.getProducts().length;
		Server remote = null;
		Product[] remote_product_list = null;

		String candidate_1 = this.server_name;
		int candidate_1_entries = local_data_no;
		String candidate_2 = null;
		int candidate_2_entries = 0;

		//ensure that the distributer is the primary server
		if( is_primary )
		{
			try
			{
				//retrieve the status of data among the system
				for( String server_name : server_list )
				{
					if( server_name.equals( this.server_name ) ) continue; //to not compare this server with itself

					try
					{
						remote = (Server)Naming.lookup( server_name ); //connect to secondary
						remote_product_list = remote.getProductData(); //get list of server's products
						int remote_entries = remote_product_list.length;

						System.out.println( "getCandidates: Products on " + server_name + " = " + remote_entries );
						System.out.println( "getCandidiates: Currently C1 = " + candidate_1 + " with " + candidate_1_entries +
							" and C2 = " + candidate_2 + " with " + candidate_2_entries );

						if( candidate_2 == null )
						{
							System.out.println( "getCandidates: Server " + server_name + " has " + remote_entries +
								" storing as 2nd candidate" );

							//no second candidate has been found yet, make current server a candidate
							candidate_2 = server_name;
							candidate_2_entries = remote_entries;
						}
						else if( remote_entries < candidate_1_entries )
						{
							System.out.println( "getCandidates: Candidate " + candidate_1 + " has " + candidate_1_entries +
								" and " + server_name + " has " + remote_entries + " storing as 1st candidate" );

							//current server is a better candidate than candidate 1
							candidate_1 = server_name;
							candidate_1_entries = remote_entries;
						}
						else if( remote_entries < candidate_2_entries )
						{
							System.out.println( "getCandidates: Candidate " + candidate_2 + " has " + candidate_2_entries +
								" and " + server_name + " has " + remote_entries + " storing as 2nd candidate" );

							//current server is a better candidate than candidate 2
							candidate_2 = server_name;
							candidate_2_entries = remote_entries;
						}
					}
					catch( ConnectException e )
					{
						//could not connect to current server, skip and check next
						continue;
					}
				}

				//gather collected candidates ready to return
				candidates[0] = candidate_1;
				candidates[1] = candidate_2;

				System.out.println( "getCandidates: Returning canidate 1 as : " + candidates[0] );
				System.out.println( "getCandidates: Returning canidate 2 as : " + candidates[1] );
			}
			catch( Exception e )
			{
				System.out.println( "scaleData: Could not scale data: " + e );
			}
		}

		return candidates;
	}

	//retrieve locally stored primary server reference
	public Server getPrimary()
	{
		Server remote_primary = this.remote_primary;

		//primary should not need to run an election
		if( !this.is_primary )
		{
			try
			{
				remote_primary.ping(); //check if primary is alive
			}
			catch( Exception e )
			{
				System.out.println( "getPrimary: Primary dead, running election" );
				startElection(); //run election for new primary
				remote_primary = this.remote_primary;
			}

			try
			{
				System.out.println( "getPrimary: returning primary as: " + remote_primary.getName() );
			}
			catch( Exception e )
			{
				System.out.println( "getPrimary: Could not return primary: " + e );
			}
		}

		return remote_primary;
	}

	//retrieve list of registered servers
	public String[] getServerList()
	{
		System.out.println( "getServerList: Entered method" );

		String[] server_list = null;
		Server server;
		ArrayList<String> failed_servers = new ArrayList<String>();
		ArrayList<String> active_servers = new ArrayList<String>();

		System.out.println( "getServerList: About to get local servers" );
		//retrieve list of any local servers
		try
		{
			server_list = Naming.list( "rmi://localhost:1099/" ); //find servers
		}
		catch( Exception e )
		{
			System.out.println( "getServerList: Could not retrieve server list: " + e );
		}

		//do not check local machines if none have been found in registry
		if( server_list != null )
		{
			System.out.println( "getServerList: About to test local servers" );
			//check what local servers are still active
			for( String server_name : server_list )
			{
				if( rmiTest( server_name ) )
				{
					active_servers.add( server_name ); //populate list of active servers
				}
				else
				{
					failed_servers.add( server_name ); //populate list of unresponsive servers
				}
			}
		}

		System.out.println( "getServerList: About to check for known servers" );
		//retrieve list of any other known servers from database
		for( String server_name : database.getStoredServerList() )
		{
			System.out.println( "getServerList: About to test " + server_name );
			if( rmiTest( server_name ) )
			{
				//add to active servers if recieved response from remote server
				active_servers.add( server_name );
				System.out.println( "getServerList: Added " + server_name + " to active servers" );
			}
		}

		System.out.println( "getServerList: About to check failed servers" );
		if( failed_servers.size() > 0 )
		{
			//remove all failed servers from the registry
			for( Iterator<String> server_name = failed_servers.iterator(); server_name.hasNext(); )
			{
				try
				{
					Naming.unbind( server_name.next() ); //unbind server
				}
				catch( Exception e )
				{
					System.out.println( "getServerList: Failed to unbind failed server : " + e );
					continue;
				}
			}
		}

		System.out.println( "getServerList: Number of active servers added = " + active_servers.size() );

		server_list = new String[ active_servers.size() ];
		active_servers.toArray( server_list ); //convert active server list ready for return

		System.out.println( "getServerList: server_list length = " + server_list.length );
		if( server_list != null )
		{
			//print retreived server list before returning
			System.out.println( "getServerList: Returning server list as ->" );
			int counter = 0;
			for( String element : server_list )
			{
				System.out.println( "getServerList: Returning element " + counter + " = " + element );
				counter++;
			}
		}
		else
		{
			System.out.println( "getServerList: Returning server list as null" );
		}

		System.out.println( "getServerList: About to return list: " + server_list );
		return server_list;
	}

	//start election of primary server
	private void startElection()
	{
		int id = getElectionId();
		String[] server_list = getServerList();
		Server server;
		String highest = this.server_name;
		boolean election_settled;

		System.out.println( "startElection: Entered method, starting election" );

		do
		{
			election_settled = true;
			for( String server_name : server_list )
			{
				try
				{
					//check that current server name is not this server's name
					if( !server_name.equals( this.server_name ) )
					{
						System.out.println( "startElection: About to connect to server: " + server_name );
						server = (Server)Naming.lookup( server_name );

						//check if remote server is higher ranked, if so, store as possible primary
						int remote_id = server.getElectionId();

						System.out.println( "startElection: Retrieved server ID = " + remote_id );

						if( remote_id > id )
						{
							highest = server_name;
							System.out.println( "startElection: Highest ranked server now = " + highest );
						}
						else if( remote_id == id )
						{
							System.out.println( "startElection: Remote ID = " + remote_id + "and local = " + id );

							election_settled = false; //election will not resolve, reattempt required
							createElectionId(); //create new election id for election retry
						}
					}
				} catch( Exception e ) {}
			}
		} while( !election_settled ); //continue to run election until resolved

		//inform other servers of change in primary
		for( String server_name : server_list )
		{
			//inform review servers of the updated primary
			try
			{
				server = (Server)Naming.lookup( server_name );
				server.changePrimary( highest );
			}
			catch( Exception e ) {}
		}

		System.out.println( "startElection: Election process complete" );
	}

	//generate an election id used to determine primary server
	private void createElectionId()
	{
		Random generator = new Random();

		int id = generator.nextInt( 65536 ); //get random integer between 0 & 65535 inclusive
		this.election_id = id; //update server's election id

		System.out.println( "createElectionId: Created election ID = " + this.election_id );
	}

	//retrieve server's election id
	public int getElectionId()
	{
		return this.election_id;
	}
}
