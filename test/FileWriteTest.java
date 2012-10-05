import java.rmi.*;
import java.util.*;

public class FileWriteTest
{
	public static void main( String[] args )
	{
		String server_name = args[0];
		Server server = null;
		
		try
		{	
			String host = "rmi://localhost/" + server_name;
			server = (Server) Naming.lookup( host );
			
			String[] server_list = server.getDatabase().getStoredServerList();
			
			for( String name : server_list )
			{
				System.out.println( "Server = " + name );
			}
		}
		catch( Exception e )
		{
			System.out.println( "Something went wrong: " + e );
		}
	}
}
