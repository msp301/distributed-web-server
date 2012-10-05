import java.io.*;
import java.util.*;

public class Product implements Serializable
{
	String name;
	String desc;
	String[][] review = new String[10][3];

	ArrayList<String> stored_locations = new ArrayList<String>();

	Product(String n, String d)
	{
		name = n;
		desc = d;
	}

	public boolean addReview(String n, String s, String r)
	{
		for(int i = 0; i < review.length; i++)
		{
			if(review[i][0] == null)
			{
				review[i][0] = n;
				review[i][1] = s;
				review[i][2] = r;
				return true;
			}
		}
		return false;
	}

	public String getName()
	{
		return name;
	}

	public String getDesc()
	{
		return desc;
	}

	public String[] getRev(String i)
	{
		return review[Integer.parseInt(i)];
	}

	public String[][] getRev()
	{
		return review;
	}

	public boolean deleteReview(String ID)
	{
		try
		{
			int del = Integer.parseInt(ID);
			System.out.println("<<"+del);
			review[del-1][0] = null;
			review[del-1][1] = null;
			review[del-1][2] = null;
			return true;
		}
		catch (NumberFormatException e) {}
		return false;
	}

	//retrieve list of servers storing this product entry
	public String[] getStoredLocations()
	{
		return (String[]) this.stored_locations.toArray();
	}

	//add server to list of stored product entry locations
	public void addStoredLocation( String server_name )
	{
		this.stored_locations.add( server_name );
	}
}
