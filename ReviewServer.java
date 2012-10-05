import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.lang.String;

public class ReviewServer
{
	public static void main( String[] args )
	{
		// check a server name was provided
		if( args.length == 0 )
		{
			System.out.println( "No server name provided" );
		}
		else
		{
			// name with which we can find it = user name
			String name = args[0];
			String host = "localhost";

			try
			{
				//check whether to create a primary server or not, based on given paramters
				boolean primary_server = false;
				if( args.length >= 2 && args[1].equals( "true" ) )
					primary_server = true;

				DatabaseHandler database = new DatabaseHandler( host, 1099, name, primary_server ); //create database

				//create replication service for server
				Replicator replicator = new Replicator( name, host, primary_server, database );

				//create new server implementation instance
				Impl server = new Impl( name, primary_server, database, replicator );

				// register with nameserver
				Registry r = LocateRegistry.getRegistry(host);
				Naming.rebind( "//"+host+":1099/"+name, server );
				System.out.println( "Started Server, registered as " + name );

				//allow replication service to connect to network now that this server has joined
				replicator.connect();
				System.out.println( "Replication service connected" );
			}
			catch( Exception e )
			{
				System.out.println( "Caught exception while registering: " + e );
				System.exit( -1 );
			}
		}
	}
}
