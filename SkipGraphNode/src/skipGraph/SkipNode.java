package skipGraph;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import remoteTest.Configuration;
import hashing.Hasher;
import hashing.HashingTools;

public abstract class SkipNode extends UnicastRemoteObject implements RMIInterface{
	
	
	private static final long serialVersionUID = 1L;
	protected static String address;
	protected static String nameID;
	protected static int numID;
	private static String IP ;
	private static NodeInfo[][][] lookup ;
	protected static int maxLevels = 10; 
	private static int maxData = 100;
	private static int dataNum = 0 ; 
	private static String introducer; 
	protected static int RMIPort = 1099;
	public static Scanner in = new Scanner(System.in);
	private static HashMap<Integer,Integer> dataID;
	protected static ArrayList<NodeInfo> data;
	private static Hasher hasher;
	public static final int TRUNC = 10;
	
	/*
	 * Constructor for SkipNode class
	 */
	protected SkipNode() throws RemoteException{
		super();
		try {
			maxLevels = TRUNC;
			lookup = new NodeInfo[maxLevels+1][2][maxData];
			hasher = new HashingTools();
			dataID = new HashMap<>();
			data = new ArrayList<>();
			setInfo();
			Registry reg = LocateRegistry.createRegistry(RMIPort);
			reg.rebind("RMIImpl", this);
			log("Rebinding Successful");
			String st = Inet4Address.getLocalHost().getHostAddress();
			System.setProperty("java.rmi.server.hostname",st);
		}catch (UnknownHostException e) {
			System.err.println("Unknown Host Exception in constructor. Please terminate the program and try again.");
		}
	}
	/*
	 * This method initializes the information of the current node
	 * and prints them to console
	 */
	public static void setInfo() {
		String numInput ;
		// get introducer information
		log("Enter the address of the introducer:");
		introducer = get();
		while(!(introducer.equalsIgnoreCase("None") || validateIP(introducer))) {
			log("Invalid IP. Please enter a valid IP address ('none' if original node): ");
			introducer = get();
		}
		// get Port number
		log("Enter RMI port: ");
		numInput = get();
		while(!numInput.matches("0|[1-9][0-9]*")) {
			log("Invalid port. Enter a valid port number for RMI:");
			numInput = get();			
		}
		RMIPort = Integer.parseInt(numInput);
		
		try { // Assign address and IP 
			address = Inet4Address.getLocalHost().getHostAddress() +":"+ RMIPort; //Used to get the current node address.
			IP = Inet4Address.getLocalHost().getHostAddress();
			log("My Address is :" + address);
		}catch(UnknownHostException e) {
			System.out.println("Couldn't fetch local Inet4Address. Please restart.");
			System.exit(0);
		}
		
		// The nameID and numID are hash values of the address
		nameID = hasher.getHash(address,TRUNC);
		numID = Integer.parseInt(nameID,2);
		
		// In case the introducer to this node is null, then the insert method
		// will not be called on it, so we manually add it to the data list and 
		// map index in the data array with its numID.
		if(introducer.equalsIgnoreCase("none")) {
			data.add(new NodeInfo(address,numID,nameID));
			dataID.put(numID, 0);
			dataNum++;
		}
		log("Your INFO:\nnameID: "+nameID+"\nnumID: "+numID+"\nintroducer: "+introducer);
	}

	/*
	 * This method is a helper method for insert() method
	 * It is used to make the insert() operation recursive per level.
	 * It receives the level of insertion and the direction of search
	 * if direction == 1, then the search is to the right, and the method returns the right neighbor
	 * if direction != 1, then the search is to the left, and the method returns the left neighbor
	 * This method is also directly accessed by other nodes using RMI if a search should pass through it
	 * @see RMIInterface#insertSearch(int, int, int, java.lang.String)
	 */
	public NodeInfo insertSearch(int level, int direction,int num, String target) throws RemoteException {
		
		int dataIdx = dataID.get(num);
		// If the current node and the inserted node have common bits more than the current level,
		// then this node is the neighbor so we return it
		if(commonBits(target) > level) 
			return data.get(dataIdx);
		// If search is to the right then delegate the search to right neighbor if it exists
		// If the right neighbor is null then at this level the right neighbor of the inserted node is null
		if(direction == 1) {
			if(lookup[level][1][dataIdx] == null)
				return null;
			RMIInterface rRMI = getRMI(lookup[level][1][dataIdx].getAddress());
			return rRMI.insertSearch(level,direction,lookup[level][1][dataIdx].getNumID(),target);
		} else {
			// If search is to the left then delegate the search to the left neighbor if it exists
			// If the left neighbor is null, then the left neighbor of the inserted node at this level is null.
			if(lookup[level][0][dataIdx] == null)
				return null;
			RMIInterface lRMI = getRMI(lookup[level][0][dataIdx].getAddress());
			return lRMI.insertSearch(level,direction,lookup[level][0][dataIdx].getNumID(),target);
		}
	}
	
	/*
	 * This method inserts either the current node to the skip graph of the introducer,
	 * or it is used to insert a data node.
	 */
	public void insert(NodeInfo node){
		try {
			String left = null;
			String right = null;

			// We search through the introducer node to find the node with 
			// the closest num ID
			NodeInfo position ;
			if(introducer.equalsIgnoreCase("none")) {
				position = searchByNumID(node.getNumID());
			}else {
				RMIInterface introRMI = getRMI(introducer);		
				position = introRMI.searchByNumID(node.getNumID());
			}
			
			if(position == null) {
				log("The address resulting from the search is null");
				log("Please check the introducer's IP address and try again.");
				return;
			}
			else
				log("The address resulting from the search is: " + position.getAddress());
			
			RMIInterface posRMI = getRMI(position.getAddress());
			if(posRMI == null) {
				log("RMI registry lookup at address: "+position+" failed. Insert operation stopped.");
				return;
			}
			
			// First, we insert the node at level 0
			
			int posNum = position.getNumID(); // numID of the closest node
			String posName = position.getNameID(); // nameID of the closest node
			int leftNum = -1 ; // numID of left node
			int rightNum = -1 ; // numID of right node
			
			if(posNum > node.getNumID()) { // if the closest node is to the right
				
				right = position.getAddress();
				left = posRMI.getLeftNode(0,posNum); // the left of my right will be my left
				rightNum = position.getNumID(); // we need the numID to be able to access it
				
				if(left != null) { // insert the current node in the lookup table of my left node if it exists
					RMIInterface leftRMI = getRMI(left);
					leftNum = posRMI.getLeftNumID(0,posNum);
					lookup[0][0][dataNum] = new NodeInfo(left,leftNum,posRMI.getLeftNameID(0,posNum));
					leftRMI.setRightNode(0, node,leftNum);
				}
				
				lookup[0][1][dataNum] = new NodeInfo(right,posNum,posName);
				posRMI.setLeftNode(0, node, posNum); // insert the current node in the lookup table of its right neighbor
			
			}else{ // if the closest node is to the left
				
				right  = posRMI.getRightNode(0,posNum); // the right of my left is my right
				left = position.getAddress() ; 
				leftNum = position.getNumID(); // we need the numID to be able to access it
				
				if(right != null) { // insert current node in the lookup table of its right neighbor if it exists
					RMIInterface rightRMI = getRMI(right);
					rightNum = posRMI.getRightNumID(0, posNum);
					lookup[0][1][dataNum] = new NodeInfo(right,rightNum,posRMI.getRightNameID(0,posNum)) ;
					rightRMI.setLeftNode(0,node,rightNum);
				}
				
				lookup[0][0][dataNum] = new NodeInfo(left,posNum,posName);
				posRMI.setRightNode(0, node,posNum);
				
			}
			
			// Now, we insert the node in the rest of the levels
			// In level i , we make a recursive search for the nodes that will be
			// the neighbors of the inserted nodes at level i+1
			
			int level = 0;
			while(level < maxLevels) {
				
				if(left != null) {
					
					RMIInterface leftRMI = getRMI(left);
					NodeInfo lft = leftRMI.insertSearch(level,-1,leftNum,node.getNameID()); // start search left
					lookup[level+1][0][dataNum] = lft ; 
					
					// set left and leftNum to default values (null,-1)
					// so that if the left neighbor is null then we no longer need
					// to search in higher levels to the left
					left = null;
					leftNum = -1;
					
					if(lft != null) {
						RMIInterface lftRMI = getRMI(lft.getAddress());
						lftRMI.setRightNode(level+1,node, lft.getNumID());
						left = lft.getAddress();
						leftNum = lft.getNumID();
					}	
				}
				if(right != null) {
					
					RMIInterface rightRMI = getRMI(right);
					NodeInfo rit = rightRMI.insertSearch(level, 1, rightNum, node.getNameID()); // start search right
					lookup[level+1][1][dataNum] = rit;
					
					// set right and rightNum to default values (null,-1)
					// so that if the right neighbor is null then we no longer need
					// to search in higher levels to the right
					right = null;
					rightNum = -1;
					
					if(rit != null) {
						RMIInterface ritRMI = getRMI(rit.getAddress());
						ritRMI.setLeftNode(level+1, node, rit.getNumID());
						right = rit.getAddress();
						rightNum = rit.getNumID();
					}
				}
				level++;
			}
			// after we conclude inserting the node in all levels,
			// we add the inserted node to the data array
			// and we map its numID with its index in the data array using dataID
			dataID.put(node.getNumID(),dataNum);
			data.add(node);
			dataNum++;
		}catch(RemoteException e) {
			e.printStackTrace();
			log("Remote Exception thrown in insert function.");
		}
	}
	
	/*
	 * This method receives a number, and returns the data node (or possibly the main node)
	 * which has the closest numID to the given number
	 */
	public int getBestNum(int num) {
		int dif = Math.abs(num-data.get(0).getNumID());
		int best = 0 ;
		for(int i=1 ; i<dataNum ; ++i)
			if(Math.abs(data.get(i).getNumID()-num) < dif) {
				dif = Math.abs(data.get(i).getNumID()-num);
				best = i;
			}
		return best;
	}
	
	/*
	 * This method is a hepler method for searchByNumID()
	 * It recieves the target numID and the level it is searching in,
	 * and it routes the search through the skip graph recursively using RMI
	 * @see RMIInterface#searchNum(int, int)
	 */
	public NodeInfo searchNum(int targetInt,int level){
		
		log("Search at: "+address); // we use this to see when a search has passed through this node
		
		int dataIdx = getBestNum(targetInt);// get the data node (or main node) that is closest to the target search
		int num = data.get(dataIdx).getNumID();
		if(num == targetInt) 
			return data.get(dataIdx) ;
		
		// If the target is greater than the current node then we should search right
		if(num < targetInt) {
			
			// Keep going down levels as long as there is either no right neighbor
			// or the right neighbor has a numID greater than the target
			while(level >= 0 && (lookup[level][1][dataIdx] == null || lookup[level][1][dataIdx].getNumID() > targetInt))
				level--;
			// If there are no more levels to go down to return the current node
			if(level < 0) 			
				return data.get(dataIdx);
			// delegate the search to the right neighbor
			RMIInterface rightRMI = getRMI(lookup[level][1][dataIdx].getAddress());
			try{
				return rightRMI.searchNum(targetInt,level);
			}catch(Exception e) {
				log("Exception in searchNum. Target: "+targetInt);
			}
		}
		else { // If the target is less than the current node then we should search left 
			// Keep going down levels as long as there is either no right neighbor
			// or the left neighbor has a numID greater than the target
			while(level >= 0 && (lookup[level][0][dataIdx] == null || lookup[level][0][dataIdx].getNumID() < targetInt))
				level--;
			// If there are no more levels to go down to return the current node
			if(level < 0)
				return data.get(dataIdx);
			// delegate the search to the left neighbor
			RMIInterface leftRMI = getRMI(lookup[level][0][dataIdx].getAddress());
			try{
				return leftRMI.searchNum(targetInt, level);
			}catch(Exception e) {
				log("Exception in searchNum. Target: "+targetInt);
			}
		}
		return data.get(dataIdx);
	}
	
	/* 
	 * Executes a search through the skip graph by numeric id and returns the a NodeInfo object 
	 * which contains the address, numID, and nameID of the node with closest numID to the target
	 * It starts the search from last level of the current node
	 * @see RMIInterface#searchByNumID(java.lang.String)
	 */
	public NodeInfo searchByNumID(int searchTarget){
	
		int level = maxLevels;
		int dataIdx = getBestNum(searchTarget); // route search to closest data node
		if(lookup[0][0][dataIdx] == null && lookup[0][1][dataIdx] == null)
			return data.get(dataIdx);
		return searchNum(searchTarget,level);	
	}
	
	/*
	 * This method receives a nameID and returns the index of the data node which has
	 * the most common prefix with the given nameID
	 */
	public int getBestName(String name) {
		int best = 0;
		int val = commonBits(name,data.get(0).getNameID());
		for(int i=1 ; i<dataNum ; ++i) {
			int bits = commonBits(name,data.get(i).getNameID());
			if(bits > val) {
				val = bits;
				best = i;
			}
		}
		return best;
	}
	
	/*
	 * This method is a helper method for searchByNameID()
	 * It receives the target nameID and the level it is searching in, and also the direction
	 * of search, and it routes the search through the skip graph recursively using RMI
	 * It return the most similar node if the node itself is not found
	 * The similarity is defined to be the the maximum number of common bits
	 * If direction == 1, then search is to the right 
	 * If direction == 0, then search is to the left
	 * @see RMIInterface#searchName(java.lang.String, int, int)
	 */
	public NodeInfo searchName(String searchTarget,int level,int direction) throws RemoteException {
		
		int dataIdx = getBestName(searchTarget);
		if(data.get(dataIdx).getNameID().equals(searchTarget)) // if the current node hold the same nameID, return it.
			return data.get(dataIdx);
		// calculate common bits to find to which level the search must be routed
		int newLevel = commonBits(searchTarget); 
		
		// If the number of common bits is not more than the current level
		// then we continue the search in the same level in the same direction
		if(newLevel <= level ) {
			if(lookup[level][direction][dataIdx] == null)// If no more nodes in this direction return the current node
				return data.get(dataIdx) ;
			RMIInterface rightRMI = getRMI(lookup[level][direction][dataIdx].getAddress());
			return rightRMI.searchName(searchTarget, level, direction);
		}
		// If the number of common bits is more than the current level
		// then the search will be continued on the new level
		// so we start a search in both directions in the new level
		
		NodeInfo result = data.get(dataIdx); // we initialize the result to current node
		
		// First we start the search on the same given direction and wait for the result it returns
		if(lookup[newLevel][direction][dataIdx] != null) {
			RMIInterface rightRMI = getRMI(lookup[newLevel][direction][dataIdx].getAddress());
			result = rightRMI.searchName(searchTarget,newLevel,direction);
		}
		// If it returns a result that differs from the current node then we check it
		if(result != null && !result.equals(data.get(dataIdx))) {
			RMIInterface resultRMI = getRMI(result.getAddress());
			// If this is the result we want return it, otherwise continue the search in the opposite direction
			if(resultRMI.getNameID().contains(searchTarget))
				return result;
		}
		// Continue the search on the opposite direction
		if(lookup[newLevel][1-direction] != null) {
			RMIInterface leftRMI = getRMI(lookup[newLevel][1-direction][dataIdx].getAddress());
			NodeInfo k = leftRMI.searchName(searchTarget, newLevel, 1-direction);
			if(result == null || commonBits(k.getNameID(),data.get(dataIdx).getNameID()) > commonBits(result.getNameID(),data.get(dataIdx).getNameID()))
				result = k;
		}
		return result;			
	}
	
	/*
	 * This methods starts a search by nameID, and returns the node as an instance
	 * of NodeInfo class which contains (address, numID, nameID) of the node,
	 * such that the nameID of the returned node is the most similar with the searchTarget.
	 * Similarity is defined to be the maximum number of common bits between the two strings 
	 * @see RMIInterface#searchByNameID(java.lang.String)
	 */
	public NodeInfo searchByNameID(String searchTarget) throws RemoteException{
		
		int dataIdx = getBestName(searchTarget);
		int newLevel = commonBits(searchTarget);
		NodeInfo result = data.get(dataIdx);
		
		// First execute the search in the right direction and see the result it returns
		if(lookup[newLevel][1][dataIdx] != null) {
			RMIInterface rightRMI = getRMI(lookup[newLevel][1][dataIdx].getAddress());
			result = rightRMI.searchName(searchTarget,newLevel,1);
		}
		// If the result is not null and is different from the default value we check it
		if(result != null && !result.equals(data.get(dataIdx))) {
			RMIInterface resultRMI = getRMI(result.getAddress());
			// If this is the result we want return it, otherwise continue searching to the left
			if(resultRMI.getNameID().contains(searchTarget))
				return result;
		}
		// If the desired result was not found try to search to the left
		if(lookup[newLevel][0][dataIdx] != null) {
			RMIInterface leftRMI = getRMI(lookup[newLevel][0][dataIdx].getAddress());
			NodeInfo k = leftRMI.searchName(searchTarget, newLevel, 0);
			if(commonBits(k.getNameID(),data.get(dataIdx).getNameID()) > commonBits(result.getNameID(),data.get(dataIdx).getNameID()))
				result = k;
		}
		return result;
	}
	/*
	 * getters and setters for lookup table and numID and nameID
	 * 
	 */
	public String getLeftNode(int level,int num) throws RemoteException {
		if(lookup[level][0][dataID.get(num)] == null)
			return null;
		return lookup[level][0][dataID.get(num)].getAddress();
	}
	
	public String getRightNode(int level, int num) throws RemoteException {
		if(lookup[level][1][dataID.get(num)] == null)
			return null;
		return lookup[level][1][dataID.get(num)].getAddress();
	}
	public void setLeftNode(int level,NodeInfo newNode, int num) throws RemoteException{
		log("LeftNode at level "+level+" set to: "+newNode.getAddress());
		lookup[level][0][dataID.get(num)] = newNode;
	}
	public void setRightNode(int level,NodeInfo newNode, int num) throws RemoteException {
		log("RightNode at level "+level+" set to: "+newNode.getAddress());
		lookup[level][1][dataID.get(num)] = newNode ;
	}
	public int getNumID(){
		return numID;
	}
	public String getNameID() {
		return nameID ;
	}
	protected static int getDataNum() {
		return dataNum;
	}
	public String getAddress() {
		return address;
	}
	protected static int getRMIPort() {
		return RMIPort;
	}
	protected void setNumID(int num) {
		numID = num;
	}
	protected void setNameID(String s) {
		nameID = s;
	}
	public int getLeftNumID(int level,int num) {
		return lookup[level][0][dataID.get(num)].getNumID();
	}
	public int getRightNumID(int level, int num) {
		return lookup[level][1][dataID.get(num)].getNumID();
	}
	public String getLeftNameID(int level,int num) {
		return lookup[level][0][dataID.get(num)].getNameID();
	}
	public String getRightNameID(int level,int num) {
		return lookup[level][1][dataID.get(num)].getNameID();
	}
	public NodeInfo getNode(int num) {
		return data.get(dataID.get(num));
	}
	
	/*
	 * This method returns an RMI instance of the node with the given address
	 */
	public static RMIInterface getRMI(String adrs) {		
		if(validateIP(adrs))
			try {
				return (RMIInterface)Naming.lookup("//"+adrs+"/RMIImpl");
			}catch(Exception e) {
				log("Exception while attempting to lookup RMI located at address: "+adrs);
			}
		else {
			log("Error in looking up RMI. Address: "+ adrs + " is not a valid address.");
		}
		return null;
	}
	
	/*
	 * This method validates the ip and makes sure its of the form xxx.xxx.xxx.xxx
	 */
	private static boolean validateIP(String adrs) { 
		int colonIndex = adrs.indexOf(':');
		String ip = adrs;
		if(colonIndex != -1) ip = adrs.substring(0,colonIndex);
		String[] parts = ip.split("\\.");
		if(parts.length!=4) {
			return false;
		}
		try {
			for(String el : parts) {
				int num = Integer.parseInt(el);
				if(num<0||num>255) return false;
			}
		}catch(NumberFormatException e) {
			return false;
		}
		if(ip.endsWith("."))
			return false;
		return true;
	}
	
	// This method calculate the length of the common prefix 
	// between the nameID of the current node and the given name
	public static int commonBits(String name) {	
		if(name.length() != nameID.length())
			return -1;
		int i = 0 ;
		for(i = 0 ; i < name.length() && name.charAt(i) == nameID.charAt(i);i++);
		log("Common Prefix for " + nameID + " and " + name + " is: " + i);
		return i ;		
	}
	/*
	 * This method returns the length of the common prefix between two given strings
	 */
	public static int commonBits(String name1, String name2) {
		if(name1 == null || name2 == null) {
			return -1;
		}
		if(name1.length() != name2.length())
			return -1;
		int i = 0;
		for(i = 0; i < name1.length() && name1.charAt(i) == name2.charAt(i) ; ++i);
		log("Common Prefix for " + name1 + " and " + name2 + " is: " + i);
			return i;		
		}
	/*
	 * A shortcut for printing to console
	 */
	public static void log(String s) {
		System.out.println(s);
	}
	public static void logLine(String s) {
		System.out.print(s);
	}
	/* 
	 * Print the contents of the lookup table
	 */
	public static void printLookup(int num) {
        System.out.println("\n");
        for(int i = maxLevels-1 ; i >= 0 ; i--)
        {
            for(int j = 0 ; j<2 ; j++)
            	if(lookup[i][j][num] == null)
            		logLine("null\t");
            	else
            		logLine(lookup[i][j][num].getAddress()+"\t");
            log("\n\n");
        }
    }
	/*
	 * A shortcut for getting input from user
	 */
	public static String get() {
		String response = in.nextLine();
		return response;
	}
	
	// For Testing:
	private static int configurationsLeft = 0;
	private static ArrayList<Configuration> cnfs; //0th = master nodeinfo
	public ArrayList<NodeInfo> getData() throws RemoteException{
		return data;
	}
	public NodeInfo[][][] getLookupTable() throws RemoteException{
		return lookup;
	}

}