import java.io.*;

public class User implements Serializable
{
	private String name;
	private String password;
	private String email;
	private String[] config = new String[3];

	User(String[] details )
	{
		name = details[0];
		password = details[1];
		email = details[2];
		config[0] = "LemonChiffon";
		config[1] = "Black";
		config[2] = "Verdana";
	}

	public String getName()
	{
		return name;
	}

	public String getPassword()
	{
		return password;
	}

	public String getEmail()
	{
		return email;
	}
	
	public String[] getDetails()
	{
		return new String[]{name,password,email};
	}
	
	public void setPassword(String a)
	{
		password = a;
	}
	
	public void setEmail(String a)
	{
		email = a;
	}
	
	public void setConfig(int type, String val)
	{
		config[type] = val;
	}
	
	public String[] getConfig()
	{
		return config;
	}
}
