import java.rmi.*;
import java.util.*;

public class ScaleDataTest
{
	public static void main( String[] args )
	{
		String server_name = args[0];
		Server server = null;

		try
		{
			String name = "rmi://localhost/" + server_name;
			server = (Server) Naming.lookup( name );

			System.out.println( "Connected to server: " + server.getName() );
			//server.createProduct( "iPod", "yay for me" );
			server.createProduct( "stuff", "yay for me" );
			//server.removeProduct( "meme" );

			//String[] details = { "martin", "1234", "mail@meme.com" };
			//server.completeReg( details );
			//String[] details2 = { "abi", "5678", "abi@internets.org" };
			//server.completeReg( details2 );
			/*User[] users = server.getUserData();

			for( User user : users )
			{
				System.out.println( "User = " + user.getName() );
			}*/

			//server.submitReview( "pool", "bob", "4", "martin is amazing :)" );

			/*String[][] reviews = server.getReview( "iPod" );
			for( String[] review : reviews )
			{
				System.out.println( "Review => " );
				System.out.println( "User = " + review[0] );
				System.out.println( "Score = " + review[1] );
				System.out.println( "Text = " + review[2] );
			}*/

			//String[] products = server.getProductList( "" );

			//for( String product : products )
			//{
			//	System.out.println( "Product = " + product );
			//}

			//server.removeReview( "random", )
			//server.removeProduct( "pool" );

			String[] product = server.getProduct( "iPod" );
			System.out.println( "Name = " + product[0] );
			System.out.println( "Desc = " + product[1] );

			//server.getProductList( "" );

			//DatabaseHandler database = new DatabaseHandler();
			//database.addProductEntry( "hello", "random", "//localhost:1099/mypretend" );
			//database.addStoredLocation( "hello", "//otherserverplace:1099/yepanewone" );

			//database.addProductEntry( "martin", "same text", "//random:1099/place" );

			/*Hashtable data = database.getLocationList();

			Enumeration e = data.keys();
			while( e.hasMoreElements() )
			{
				String product_name = ( String ) e.nextElement();
				System.out.println( "Element " + product_name + " => " );

				Iterator i = ( ( ArrayList ) data.get( product_name ) ).iterator();
				while( i.hasNext() )
				{
					String server_id = ( String ) i.next();
					System.out.println( "Server = " + server_id );
				}
			}*/
		}
		catch( Exception e )
		{
			System.out.println( "Caught Exception: " + e );
		}
	}
}
