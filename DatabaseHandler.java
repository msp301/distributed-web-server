import java.io.*;
import java.util.*;

public class DatabaseHandler implements Serializable
{
	ArrayList<Product> product_list;
	ArrayList<User> user_list;
	Hashtable<String, ArrayList<String>> stored_locations;
	Hashtable<String, ArrayList<String>> user_locations;
	ArrayList<String[]> load_list;
	ArrayList<String> known_servers;
	int load = 0;
	String server_name;

	public DatabaseHandler( String host, int port, String name, boolean is_primary )
	{
		product_list = new ArrayList<Product>(); //create array list containing only products
		user_list = new ArrayList<User>();
		load_list = new ArrayList<String[]>();
		known_servers = new ArrayList<String>(); //list of known remote servers
		server_name = name;

		stored_locations = new Hashtable<String, ArrayList<String>>(); //hash containing stored locations of products
		user_locations = new Hashtable<String, ArrayList<String>>();

		System.out.println( "Created DatabaseHandler" );
	}

	//clear all elements from database
	public void resetDatabase()
	{
		product_list.clear(); //remove all list elements
	}

	//add product to database
	public boolean addProductEntry( String name, String desc, String server_name )
	{
		boolean success = true;

		Product product = new Product( name, desc ); //create new product

		getLocationList().put( name, new ArrayList<String>() ); //add new product entry into stored locations list
		addStoredLocation( name, server_name ); //add this server into the product stored locations list

		this.product_list.add( product ); //add new product to database
		updateFile( getProductFileName(), getProducts() ); //update database file with new entry

		System.out.println( "addProductEntry: Added new product " + name );

		return success;
	}

	//add product to database using a product object
	public boolean addProductEntry( Product product, String server_name )
	{
		boolean success = true;

		this.product_list.add( product ); //add product to database
		addStoredLocation( product.getName(), server_name ); //update stored locations of product

		return success;
	}

	//add product review
	public boolean addProductReview(
		String name,
		String user,
		String score,
		String review )
	{
		boolean success = true;
		Product product = getProductEntry( name ); //find product entry

		System.out.println( "addProductReview: Retrived Entry " + product );

		if( product != null )
		{
			//add product review
			product.addReview( user, score, review );
		}
		else
		{
			success = false;
		}

		return success;
	}

	//remove product from database
	public boolean deleteProduct( String name )
	{
		boolean success = true;

		Iterator<Product> products = this.product_list.iterator();
		while( products.hasNext() )
		{
			Product entry = products.next();
			if( entry.getName().equals( name ) )
			{
				success = this.product_list.remove( entry );
				removeStoredLocation( name ); //remove product entry
				break;
			}
		}

		return success;
	}

	//remove product review from database
	public boolean removeReviewEntry( String name, String review_id )
	{
		boolean success = true;

		Product product = getProductEntry( name ); //retrieve product

		if( product != null )
		{
			product.deleteReview( review_id ); //remove product review
		}
		else
		{
			success = false;
		}

		return success;
	}

	//retrieve product from database
	public Product getProductEntry( String name )
	{
		Product product = null;

		for( Product entry : getProducts() )
		{
			if( entry != null && entry.getName().equals( name ) )
			{
				product = entry;
				break;
			}
		}

		return product;
	}

	//retrieve product objects
	public Product[] getProducts()
	{
		int list_size = this.product_list.size();
		Product[] product_array = new Product[ list_size ];
		this.product_list.toArray( product_array );

		return product_array; //return products
	}

	public User[] getUsers()
	{
		int list_size = this.user_list.size();
		User[] user_array = new User[ list_size ];

		this.user_list.toArray( user_array );

		return user_array; //return users
	}

	//retrieve all reviews for a product from the database
	public String[][] getProductReviews( String name )
	{
		String[][] reviews = null;
		Product product = getProductEntry( name );

		if( product != null )
		{
			reviews = product.getRev(); //get product review
		}

		return reviews;
	}

	//retrieve product stored locations list
	public Hashtable<String, ArrayList<String>> getLocationList()
	{
		return this.stored_locations;
	}

	//add a new stored server location for a product
	public void addStoredLocation( String product, String server_name )
	{
		//check whether product is already listed
		if( !getLocationList().containsKey( product ) )
		{
			getLocationList().put( product, new ArrayList<String>() ); //add new product entry into stored locations list
		}

		//retrieve location list for given product and add a new server entry
		if( !getLocationList().get( product ).contains( server_name ) )
		{
			getLocationList().get( product ).add( server_name );
		}
	}

	//remove stored location of a product
	public void removeStoredLocation( String product, String server_name )
	{
		//check whether product is already listed
		if( getLocationList().containsKey( product ) )
		{
			getLocationList().get( product ).remove( server_name ); //remove a selected server name from stored list
		}
	}

	//remove all stored locations of a product
	public void removeStoredLocation( String product )
	{
		//check whether product is already listed
		if( getLocationList().containsKey( product ) )
		{
			getLocationList().remove( product ); //remove entire product entry record
		}
	}

	//retrieve list of servers storing this product entry
	public String[] getStoredLocation( String name )
	{
		ArrayList<String> locations = getLocationList().get( name );
		String[] locations_arr = new String[ locations.size() ];
		locations.toArray( locations_arr );

		return locations_arr;
	}

	//update database's product location list
	public void setLocationList( Hashtable<String, ArrayList<String>> location_list )
	{
		this.stored_locations = location_list; //overwrite current list with updated version
	}

	//------ User account control ------

	public boolean createUser( String[] details, String server_name )
	{
		String name = details[0];
		String password = details[1];
		String email = details[2];

		getUserLocationList().put( name, new ArrayList<String>() ); //add new product entry into stored locations list
		addUserLocation( name, server_name ); //add this server into the product stored locations list

		this.user_list.add( new User( details ) );
		return true;
	}

	public String getUserDetail(String user,String detail,String value)
	{
		try
		{
			if(detail.equals("password"))
			{
				return getUser(user).getPassword();
			}
			else if(detail.equals("email"))
			{
				return getUser(user).getEmail();
			}
		}
		catch(Exception e){}
		return null;
	}

	public String[] getUserDetails(String user)
	{
		try
		{
			return getUser(user).getDetails();
		}
		catch(Exception e){}
		return null;
	}

	public boolean setUserDetail(String user, String detail, String value)
	{
		try
		{
			if(detail.equals("password"))
			{
				getUser(user).setPassword(value);
			}
			else if(detail.equals("email"))
			{
				getUser(user).setEmail(value);
			}
			return true;
		}
		catch(Exception e){}
		return false;
	}

	public boolean deleteUser(String name)
	{
		boolean success = true;

		Iterator users = this.user_list.iterator();
		while( users.hasNext() )
		{
			User entry = (User) users.next();
			if( entry.getName().equals( name ) )
			{
				success = this.user_list.remove( entry );
				removeUserLocation( name ); //remove user entry
				break;
			}
		}

		return success;
	}

	public User getUser(String name)
	{
		User user = null;

		for( User entry : getUsers() )
		{
			if( entry != null && entry.getName().equals( name ) )
			{
				user = entry;
				break;
			}
		}

		return user;
	}

	public String[] getUserConfig(String name)
	{
		User user = getUser(name);
		return user.getConfig();
	}

	public boolean setUserConfig(String name, String[] config)
	{
		User user = getUser(name);

		for(int i = 0; i < 3; i++)
		{
			try
			{
				if(!config[i].isEmpty())
				{
					System.out.println("Changing config "+i);
					user.setConfig(i, config[i]);
				}
			} catch (Exception e){}
		}
		return true;
	}


	//---- User account location control

	//retrieve user account stored locations list
	public Hashtable<String, ArrayList<String>> getUserLocationList()
	{
		return this.user_locations;
	}

	//add a new stored server location for a user account
	public void addUserLocation( String username, String server_name )
	{
		//check whether product is already listed
		if( !getUserLocationList().containsKey( username ) )
		{
			getUserLocationList().put( username, new ArrayList<String>() ); //add new user account into stored locations list
		}

		//retrieve location list for given product and add a new server entry
		getUserLocationList().get( username ).add( server_name );
	}

	//remove stored location of a user account
	public void removeUserLocation( String username, String server_name )
	{
		//check whether product is already listed
		if( getUserLocationList().containsKey( username ) )
		{
			getUserLocationList().get( username ).remove( server_name ); //remove a selected server name from stored list
		}
	}

	//remove all stored locations of a user account
	public void removeUserLocation( String username )
	{
		//check whether product is already listed
		if( getUserLocationList().containsKey( username ) )
		{
			getUserLocationList().remove( username ); //remove entire product entry record
		}
	}

	//retrieve list of servers storing a user account
	public String[] getUserLocation( String username )
	{
		ArrayList<String> locations = getUserLocationList().get( username );
		String[] locations_arr = new String[ locations.size() ];
		locations.toArray( locations_arr );

		return locations_arr;
	}

	//----------------------------------

	public boolean setLoadVal(String[] load)
	{
		int count = 0;
		try
		{
			for(int i = 0; i < load_list.size(); i++)
			{
				if(load_list.get(i)[0].contains(load[0]))
				{
					load_list.set(i,load);
					return true;
				}
				else
				{
					count++;
				}
			}
		}
		catch (Exception e){}

		load_list.add(count, load);
		System.out.println("RAWR "+ count +" "+ load_list.get(count)[0] + " "+ load_list.get(count)[1]);
		//load_list.set(count,load);
		return true;
	}

	public ArrayList<String[]> getLoad()
	{
		return load_list;
	}

	public void setLoadList(ArrayList<String[]> list)
	{
		load_list = list;
	}


	//----- File handing -----

	//write data to server's database file
	private void updateFile( String file_name, Object data )
	{
		try
		{
			OutputStream file_data = new FileOutputStream( file_name );
			OutputStream buffer = new BufferedOutputStream( file_data );
			ObjectOutput data_out = new ObjectOutputStream( buffer );

			data_out.writeObject( data );
			data_out.close();
		}
		catch( Exception e )
		{
			System.out.println( "updateFile: Failed to update data to " + file_name );
		}
	}

	//read data from a file
	private Object readFile( String file_name )
	{
		Object contents = null;

		try
		{
			File file = new File( file_name );
			if( file.exists() )
			{
				InputStream file_data = new FileInputStream( file_name );
				InputStream buffer = new BufferedInputStream( file_data );
				ObjectInput data_in = new ObjectInputStream ( buffer );

				contents = data_in.readObject();
			}
		}
		catch( Exception e )
		{
			System.out.println( "readFile: Failed to read data in " + file_name );
		}

		return contents;
	}
	
	//read data from text files
	private String[] readFileText( String file_name )
	{
		ArrayList<String> data_out = new ArrayList<String>();
		String[] output;
		String current_line = null;

		System.out.println( "readFileText: About to read file" );
		try
		{
			InputStream file = new FileInputStream( file_name );
			BufferedReader buffer = new BufferedReader( new InputStreamReader( file ) );
			
			//read file data line by line & add to temporary storage
			while( ( current_line = buffer.readLine() ) != null )
			{
				data_out.add( new String( current_line ) );
			}
		}
		catch( Exception e )
		{
			System.out.println( "readFileText: Failed to read file " + file_name );
		}

		System.out.println( "readFileText: About to convert to array" );
		output = new String[ data_out.size() ];
		data_out.toArray( output ); //convert collected data to array

		System.out.println( "readFileText: data_out size = " + data_out.size() );

		for( String line : output )
		{
			System.out.println( "readFileText: Returning = '" + line + "'" );
		}

		return output;
	}
	
	//retrieve name of server's products filename
	private String getProductFileName()
	{
		return new String( "products_" + this.server_name ); //resolve products filename for this server
	}
	
	//retrieve name of known server list for this server
	private String getServerListFileName()
	{
		String filename = new String( "servers_" + this.server_name ); //resolve server list filename

		System.out.println( "getServerListFileName: Returning '" + filename + "'" );
		return filename;
	}
	
	//retrieve stored list of known servers from server list file
	public String[] getStoredServerList()
	{
		String[] servers = ( String[] ) readFileText( getServerListFileName() );
		
		for( String server : servers )
		{
			System.out.println( "getStoredServerList: Returned " + server );
		}
		
		return servers;
	}

	//retrieve stored list of products from products file
	public Product[] getStoredProducts()
	{
		Product[] products = ( Product[] ) readFile( getProductFileName() );
		
		//print names of products being returned
		for( Product product : products )
		{
			System.out.println( "getStoredProducts: Returned " + product.getName() );
		}
		
		return products;
	}

	//retrieve the list of known servers of this server
	private ArrayList<String> getKnownServerList()
	{
		return this.known_servers;
	}

	//add new server to list of known servers
	public boolean addServerEntry( String server_name )
	{
		boolean success = true;

		success = getKnownServerList().add( server_name );

		return success;
	}
}
