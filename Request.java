public class Request
{
	String request_type;
	String[] args;

	public Request( String request_type, String[] args )
	{
		this.request_type = request_type;
		this.args = args;
	}

	public String getType()
	{
		return this.request_type;
	}

	public String[] getArgs()
	{
		return this.args;
	}
}
