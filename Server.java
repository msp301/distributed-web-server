import java.rmi.*;
import java.util.*;

public interface Server extends Remote
{
	//product management methods
	public String[] getProductList( String search ) throws RemoteException;
	public String createProduct( String name,String desc ) throws RemoteException;
	public String[] getProduct( String name ) throws RemoteException;
	public String[][] getReview( String name ) throws RemoteException;
	public boolean submitReview( String name, String user, String score, String review ) throws RemoteException;
	public boolean removeReview( String name, String review_id ) throws RemoteException;
	public boolean removeProduct( String product_name ) throws RemoteException;

	//user account management methods
	public boolean checkUserDetails( String[] details ) throws RemoteException;
	public boolean completeReg( String[] details ) throws RemoteException;
	public boolean changeDetail( String user,String detail,String value ) throws RemoteException;
	public boolean delUser( String user ) throws RemoteException;

	//database management methods - products
	public void wipeDatabase() throws RemoteException;
	public Product[] getProductData() throws RemoteException;
	public Product getProductData( String name ) throws RemoteException;
	public boolean addProductEntry( String name, String desc, String server_name ) throws RemoteException;
	public boolean removeProductEntry( String name ) throws RemoteException;
	public void addStoredLocation( String product, String server_name ) throws RemoteException;
	public String[] getStoredLocation( String name ) throws RemoteException;
	public boolean addProductReview( String name, String user, String score, String review ) throws RemoteException;
	public boolean removeReviewEntry( String name, String review_id ) throws RemoteException;
	public void removeStoredLocation( String product, String server_name ) throws RemoteException;
	public void removeStoredLocation( String product ) throws RemoteException;

	//database management methods - user accounts
	public User[] getUserData() throws RemoteException;
	public boolean setConfig(String name, String[] config) throws RemoteException;
	public String[] getConfig(String name) throws RemoteException;
	public void addUserLocation( String username, String server_name ) throws RemoteException;
	public void removeUserLocation( String username, String server_name ) throws RemoteException;
	public void removeUserLocation( String username ) throws RemoteException;
	public String[] getUserLocation( String username ) throws RemoteException;
	public boolean createUser( String[] details, String server_name ) throws RemoteException;
	public boolean setUserConfig( String name, String[] config ) throws RemoteException;
	public boolean deleteUser( String user ) throws RemoteException;
	public boolean setUserDetail( String user, String detail, String value ) throws RemoteException;
	public String getUserDetail( String user, String detail, String value ) throws RemoteException;
	public String[] getUserConfig( String name ) throws RemoteException;

	//replication operation methods
	public int getElectionId() throws RemoteException;
	public void changePrimary( String server_name ) throws RemoteException;
	public Hashtable<String, ArrayList<String>> getLocationList() throws RemoteException;

	//server status methods
	public boolean isPrimary() throws RemoteException;
	public String getName() throws RemoteException;
	public boolean ping() throws RemoteException;
	public String[] getServerList() throws RemoteException;

	//load balancing methods
	public int getLoad() throws RemoteException;
	public void popLoadList() throws RemoteException;
	public ArrayList<String[]> getLoadList() throws RemoteException;
	public String getLowestLoad() throws RemoteException;

	//known server management
	public boolean addServerEntry( String server_name ) throws RemoteException;
}
