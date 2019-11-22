package blockchain;

import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import hashing.Hasher;
import hashing.HashingTools;
import remoteTest.Configuration;
import remoteTest.TestingLog;
import signature.DigitalSignature;
import signature.SignedBytes;
import skipGraph.NodeInfo;
import skipGraph.SkipNode;
import util.Util;

public class LightChainNode extends SkipNode implements LightChainRMIInterface {
	
	private static final long serialVersionUID = 1L;
	private static LightChainNode lightChainNode;
	private static ArrayList<Transaction> transactions;
	private static ArrayList<Block> blocks;
	private static DigitalSignature digitalSignature;
	private static Hasher hasher;
	private static HashMap<Integer,Integer> view;
	private static HashMap<Integer,Integer> viewBalance;
	private static HashMap<Integer,Integer> viewMode;
	private static int mode ; 
	private static int balance = 20;
	/*
	 * Constants
	 */
	private static final int VALIDATION_FEES = 1;
	private static int SIGNATURES_THRESHOLD = 5;
	private static final int TX_MIN = 4;
	private static final int ZERO_ID = 0;
	private static final int HONEST = 1;
	private static final int MALICIOUS = 0;
	private static final int UNASSIGNED = -1;
	private static final int INITIAL_BALANCE = 20;
	private static final int ALPHA = 12;
	static final int TRUNC = 30;
	
	/*
	 * Variables for simulations
	 */
	private static long malTrials = 0; 
	private static long malSuccess = 0;
	
	/*
	 * For slave/master operation
	 */
	protected static int testingMode = 1;/*
										0 = normal functionality
										1 = master: Gives out N configurations to first N nodes connecting to it
										2 = Leech: opens local config file and connects to the master as its introducer
										*/
	
	/*
	 * Main method is for Running the tests on the node
	 */
	public static void main(String args[]) {
	try {
		init();//Initialize IP and address + system properties
		lightChainNode = new LightChainNode();
		if(testingMode == 0) {
			setInfo();
			Util.log("Specify mode of node, enter 1 for HONEST, 0 for MALICIOUS");
			mode = Integer.parseInt(Util.getInput());
			while(mode != MALICIOUS && mode != HONEST) {
				Util.log("Incorrect input. Specify mode of node, enter 1 for HONEST, 0 for MALICIOUS");
				mode = Integer.parseInt(Util.getInput());
			}
		}else if(testingMode == 1) {
			cnfs = Configuration.parseConfigurations();
			Configuration cnf = cnfs.remove(0);
			lightChainNode.setInfo(cnf);
			mode = cnf.isMalicious()?MALICIOUS:HONEST;
			configurationsLeft = cnfs.size();
			for(int i = 0;i<cnfs.size();i++) {
				Configuration tmp = cnfs.get(i);
				tmp.setIntroducer(address);
			}
		}else {
			Configuration cnf = new Configuration();
			cnf.parseIntroducer();
			LightChainRMIInterface intro = getLightChainRMI(Util.grabIP() + ":1099");
			cnf = intro.getConf();
			lightChainNode.setInfo(cnf);
			mode = cnf.isMalicious()?MALICIOUS:HONEST;
		}
		//Initializing the testLog
		testLog = new TestingLog(mode==0);
		//Setting the SkipNode in the SkipNode class to the same thing
		SkipNode.node = lightChainNode;
		Registry reg = LocateRegistry.createRegistry(RMIPort);
		reg.rebind("RMIImpl", lightChainNode);
		Util.log("Rebinding Successful");
		System.out.println("inserting: " + new NodeInfo(address,lightChainNode.getNumID(),lightChainNode.getNameID()));
		if(testingMode == 2) lightChainNode.insert(new NodeInfo(address,lightChainNode.getNumID(),lightChainNode.getNameID()));
		System.out.println("inserting done.");
		


		while(true) {
			printMenu();
			lightChainNode.ask();
		}
	}catch(RemoteException e) {
		System.out.println("Remote Exception in main method. Terminating.");
		e.printStackTrace();
		System.exit(1);
	}catch(IOException e){
		Util.log("Error in Rebinding");
		e.printStackTrace();
	}
}
	
	
	
	
	/*
	 * A constructor for LightChainNode, in which the following happens:
	 * 	1) initialize Hasher and digital signature
	 * 	2) Get the mode of the node (Honest or Malicious)
	 * 	3) set the numID and nameID as the hash value of the public key
	 */
	public LightChainNode() throws RemoteException {
		super(TRUNC);
		digitalSignature = new DigitalSignature();
		hasher = new HashingTools();
		transactions = new ArrayList<>();
		blocks = new ArrayList<>();
		view = new HashMap<>();
		viewMode = new HashMap<>();
		viewBalance = new HashMap<>();
		String name = hasher.getHash(digitalSignature.getPublicKey().getEncoded(),TRUNC);
		setNumID(Integer.parseInt(name,2));
		Util.log("My numID is: " + Integer.parseInt(name,2));
		name = hasher.getHash(name,TRUNC);
		setNameID(name);
		Util.log("My nameID is: " + name);
	}
	
	/*
	 * This method prints the options for user controlling the node to choose.
	 * More options can be appended but require the modification of ask() method.
	 */
	public static void printMenu() throws IOException{
        Util.log("Node at the address: " + address);
        Util.log("Name ID: "+ nameID +" Number ID: " + numID);
        Util.log("Choose a query by entering it's code and then press Enter");
        Util.log("1-Insert Node");
        Util.log("2-Insert Transaction");
        Util.log("3-Insert Block");
        Util.log("4-Search By Name ID");
        Util.log("5-Search By Numeric ID");
        Util.log("6-Print the Lookup Table");
        Util.log("7-Update View Table");
        Util.log("8-Print Data");
        Util.log("9-Print Level");
	}
	
	/*
	 * The ask method is used to make it possible to test implemented functionalities.
	 * It can be modified according to what method one would like to test.
	 * Of course do not forget to modify printMenu() when modifying this method
	 */
	public void ask() throws RemoteException {
        String input = Util.getInput();
        if(!input.matches("[1-9]")) {
        	Util.log("Invalid query. Please enter the number of one of the possible operations");
        	return;
        }
		int query = Integer.parseInt(input);
		
		if(query == 1) { // insert node
			if(getDataNum() == 0) // if there are no inserted nodes yet then, then we insert the current node
				insert(new NodeInfo(getAddress(),getNumID(),getNameID()));
			else
				Util.log("Already Inserted");
		}else if (query == 2){ // insert transaction
			Util.log("Enter cont of transaction");
			String cont = Util.getInput();
			Block lstBlk = getLatestBlock();
			Util.log("The prev found is : " + lstBlk.getNumID());
			Transaction t = new Transaction(lstBlk.getH(), getNumID() ,cont, getAddress());
			boolean verified = validate(t);
			if(verified == false) {
				Util.log("Transaction validation Failed");
				return ;
			}
			Util.log("Added transaction with nameID " + lstBlk.getH());
			t.setAddress(getAddress());
			transactions.add(t);
			insert(t);
		}else if (query == 3){ // insert block
			Util.log("If the inserted block is genesis enter 0, otherwise enter 1");
			int num = Integer.parseInt(Util.getInput());
			String prev ;
			int index ;
			if(num == 1) {
				Block lstBlk = getLatestBlock();
				prev = lstBlk.getH();
				index = lstBlk.getIndex() + 1;
			}else {
				prev = numToName(0);
				index = 0;
			}
			Block b = new Block(prev,getNumID(),getAddress(),index);
			Util.log("Block inserted with " + b.getNumID() + " numID");
			insert(new NodeInfo(getAddress(),ZERO_ID,b.getH()));
			blocks.add(b);
			insert(b);
		}else if (query == 4) {// search by name ID
			Util.log("Please Enter the name ID to be searched");
			String name = Util.getInput();
			while(!name.matches("[0-1]+")) {//Makes sure the name is a binary string
				Util.log("Name ID should be a binary string. Please enter a valid Name ID:");
				name = Util.getInput();
			}
			NodeInfo result = null;
			try{
				result = searchByNameID(name);
			}catch(RemoteException e) {
				e.printStackTrace();
				Util.log("Remote Exception in query.");
			}
			Util.log("SearchByNameID result: ");
			Util.log("Address: " + result.getAddress());
			Util.log("numID: " + result.getNumID());
			Util.log("nameID: " + result.getNameID());
		}else if(query == 5) { // search by num ID
			Util.log("Please Enter the numeric ID to be searched");
			String numInput = Util.getInput();
			while(!numInput.matches("0|[1-9][0-9]*")) {
				Util.log("Invalid number entered. Please enter a valid number");
				numInput = Util.getInput();
			}
			int num = Integer.parseInt(numInput);
			NodeInfo result = searchByNumID(num);
			Util.log("SearchByNumID result: ");
			Util.log("Address: " + result.getAddress());
			Util.log("numID: " + result.getNumID());
			Util.log("nameID: " + result.getNameID());
		}else if(query == 7) { // delete dataNode{
			updateViewTable();
			viewUpdate();
		}
    }
	
	/*
	 * This method goes to the tail of the blockchain and iterates
	 * over the transactions pointing at it, and then updating the entries
	 * corresponding to the owners of the tansactions in the view table.
	 */
	void updateViewTable() {
		try {
			// get the tail of blockchain
			Block blk = getLatestBlock();
			// change numID to nameID format to search for transactions
			String name = numToName(blk.getNumID());
			// get transactions pointing at the tail
			ArrayList<Transaction> tList = getTransactionsWithNameID(name);
			if(tList == null)
				return ;
			// iterate over found transactions pointing at the blockchain
			for(int i=0 ; i<tList.size() ; ++i) {
				int owner = tList.get(i).getOwner();
				view.put(owner, blk.getNumID());
			}
			// iterate over transactions inside the block
//			tList = blk.getS();
//			for(int i=0 ; i<tList.size() ; ++i) {
//				int owner = tList.get(i).getOwner();
//				view.put(owner, blk.getNumID());
//			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * This method finds the latest block on the blockchain and takes its numID
	 * and uses it to find all transactions that have this numID as nameID and uses
	 * getTransactionsWithNameID() method to get such transactions, and if the number
	 * of found transactions if at least TX_MIN, the transactions are casted into a block
	 * and the block is sent for validation.
	 * 
	 */
	public void viewUpdate() throws RemoteException {
		
		try {
			long start = System.currentTimeMillis();
			
			Block blk = getLatestBlock();
			// Change numID to a nameID string
			if(blk == null) {
				Util.log("Error in retreiving the Latest block: not a block was returned");
				Util.log("viewUpdate terminated");
				return;
			}
			String name = numToName(blk.getNumID());
			
			Util.log("Searching for " + name + " in viewUpdate()");
			
			// Get all transaction with this nameID
			ArrayList<Transaction> tList = getTransactionsWithNameID(name);
			// If number of transactions obtained is less than TX_MIN then we terminate the process
			if(tList == null || tList.size() < TX_MIN) {
				Util.log("Cannot find TX_MIN number of transactions.");
				Util.log("Found " + tList.size() + "Transactions");
				testLog.logBlockValidation(-1, false);
				testLog.logViewUpdate(System.currentTimeMillis()-start, false);
				return;
			}
			// If there are TX_MIN transaction then add them into a new block
			Block newBlk = new Block(blk.getH(),getNumID(),getAddress(),tList,blk.getIndex()+1);
			// send the new block for PoV validation
			boolean isValidated = validate(newBlk);
			if(!isValidated) {
				testLog.logViewUpdate(System.currentTimeMillis()-start, true);
				return;
			}
			// insert new block after it was validated
			insert(newBlk);
			// contact the owner of the previous block and let him withdraw his flag data node.
			if(blk.getAddress().equals(getAddress())) {
				delete(ZERO_ID);
			}else {
				LightChainRMIInterface prevOwnerRMI = getLightChainRMI(blk.getAddress());
				prevOwnerRMI.delete(ZERO_ID);
			}
			// insert flag node for this block
			insert(new NodeInfo(getAddress(),ZERO_ID,newBlk.getH()));
			// iterate over transaction of this block and insert a transaction pointer corresponding to
			// each of the pointers, and then asks every owner to delete this node from the overlay
//			for(int i=0 ; i<tList.size(); ++i) {
//				//insert(new NodeInfo(getAddress(),newBlk.getNumID(),numToName(tList.get(i).getOwner())));
//				LightChainRMIInterface tRMI = getLightChainRMI(tList.get(i).getAddress());
//				tRMI.delete(tList.get(i).getNumID());
//			}
			long end = System.currentTimeMillis();
			
			long time = end - start;
			testLog.logViewUpdate(time, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * This method finds the latest block on the blockchain.
	 */
	public Block getLatestBlock() throws RemoteException {
		try {
			NodeInfo flag = searchByNumID(ZERO_ID);
			NodeInfo blk = searchByNumID(Integer.parseInt(flag.getNameID(),2));
			if(blk instanceof Block)
				return (Block)blk;
			else {
				Util.log(blk.getNumID() + " we returned as latest block");
				Util.log("Error in getLatestBlock(): instance returned is not a block");
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/*
	 * This method gets the numID of a peer and returns its latest transactions.
	 */
	public Transaction getLatestTransaction(int num) throws RemoteException {
		
		try {
			Transaction t = (Transaction)searchByNameID(numToName(num));
			return t;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	
	}
	
	/*
	 * This method receives the numID (Hash Value) of a block and conducts a nameID 
	 * search on it in order to find all transactions with this nameID. First it finds
	 * one node, and then goes to the linked list at last level of the skip graph which
	 * contains the found node and collects all nodes that are transactions from that level
	 * because they all will have the same nameID according to the structure of the skip graph
	 */
	public ArrayList<Transaction> getTransactionsWithNameID(String name) throws RemoteException{
		try {
			// find a transaction that has the given nameID 
			NodeInfo t = searchByNameID(name);
			// an empty list to add transaction to it and return it
			ArrayList<Transaction> tList = new ArrayList<>();
			
			
			if(t == null || !t.getNameID().equals(name)) {
				Util.log("No transaction was found with the given nameID");
				return tList;
			}
			// if the found node is a transaction then, add it to the list
			if(t instanceof Transaction)
				tList.add((Transaction)t);
			// left and right will store the address of the nodes we are visiting in left and right respectively
			NodeInfo left , right ;
			// leftNum and rightNum will store numIDs of left and right nodes, used to correctly access nodes (data nodes functionality)
			int leftNum = UNASSIGNED, rightNum = UNASSIGNED;
			// thisRMI is just used to extract information of neighbors
			LightChainRMIInterface thisRMI = getLightChainRMI(t.getAddress());
			
			// get addresses of left and right nodes, as well as their numIDs
			left = thisRMI.getLeftNode(lookup.getMaxLevels(),t.getNumID());
			if(left != null)
				leftNum = thisRMI.getLeftNumID(lookup.getMaxLevels(), t.getNumID());
			right = thisRMI.getRightNode(lookup.getMaxLevels(),t.getNumID());
			if(right != null)
				rightNum = thisRMI.getRightNumID(lookup.getMaxLevels(), t.getNumID());
			
			// now in the last level of the skip graph, we go left and right
			while(left != null) {
				LightChainRMIInterface leftRMI = getLightChainRMI(left.getAddress());
				NodeInfo node = leftRMI.getNode(leftNum);
				// if this node is a transaction add it to the list
				if(node instanceof Transaction)
					tList.add((Transaction)node);
				// now go to the left node again
				left = leftRMI.getLeftNode(lookup.getMaxLevels(), leftNum);
				if(left != null)
				leftNum = leftRMI.getLeftNumID(lookup.getMaxLevels(),leftNum);
			}
			while(right != null) {
				LightChainRMIInterface rightRMI = getLightChainRMI(right.getAddress());
				NodeInfo node = rightRMI.getNode(rightNum);
				// if this node is a transaction add it to the list
				if(node instanceof Transaction)
					tList.add((Transaction)node);
				
				// now go to the right node again
				right = rightRMI.getRightNode(lookup.getMaxLevels(), rightNum);
				if(right != null)
				rightNum = rightRMI.getRightNumID(lookup.getMaxLevels(), rightNum);
			}
			return tList;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/*
	 * This method is called by a node to validate a block that it has created.
	 * First it gets the validators of the block, and then contacts each
	 * validator and asks them to validate the block using PoV and then gets
	 * their signatures of the hash value of the block if the validation was successful
	 * and gets null otherwise.
	 */
	public boolean validate(Block blk) {
		try {
			long start = System.currentTimeMillis();
			if(mode == MALICIOUS) {
				malTrials++;
			}
			ArrayList<NodeInfo> validators = getValidators(blk);
			ArrayList<SignedBytes> sigma = new ArrayList<>();
			// add the owner's signature to the block
			SignedBytes mySign = digitalSignature.signString(blk.getH());
			sigma.add(mySign);
			blk.setSigma(sigma);
			// iterate over validators and ask them to validate the block
			for(int i=0 ; i<validators.size() ; ++i) {
				LightChainRMIInterface node = getLightChainRMI(validators.get(i).getAddress());
				SignedBytes signature = node.PoV(blk);
				// if one validator returns null, then validation has failed
				if(signature == null || signature.getBytes() == null) {
					Util.log("Validating Block failed.");
					testLog.logBlockValidation(System.currentTimeMillis()-start, false);
					return false;
				}
				sigma.add(signature);
			}
			// update the sigma array of the block with all the signatures
			blk.setSigma(sigma);
			Util.log("Validation of block is successful");
			if(mode == MALICIOUS) {
				malSuccess++;
			}
			long end = System.currentTimeMillis();
			
			long time = end - start;
			testLog.logBlockValidation(time, true);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	 * The method is called by a node to validate a transaction that it has created
	 * First it gets the validators of the transaction, and then contacts each of them 
	 * to get their signatures and stores their signatures in the sigma array.
	 */
	public boolean validate(Transaction t) throws RemoteException {
		long start = System.currentTimeMillis();
		int isAuthenticated = 0;
		int isSound = 0;
		int isCorrect = 0;
		int hasBalance = 0;
		try {
			if(mode == MALICIOUS) {
				malTrials++;
			}
			// obtain validators
			ArrayList<NodeInfo> validators = getValidators(t);
			// define an empty list
			ArrayList<SignedBytes> sigma = new ArrayList<>();
			// add the owner's signature of the transaction's hash value to the sigma
			SignedBytes mySign = digitalSignature.signString(t.getH());
			sigma.add(mySign);
			t.setSigma(sigma);
						
			boolean validated = true;
			long duration=-1;
			// iterate over validators and use RMI to ask them to validate the transaction
			for(int i=0 ; i<validators.size(); ++i) {
				LightChainRMIInterface node = getLightChainRMI(validators.get(i).getAddress());
				SignedBytes signature = node.PoV(t);
				if(signature.isAuth()) isAuthenticated++;
				if(signature.isCorrect()) isCorrect++;
				if(signature.isSound()) isSound++;
				if(signature.hasBalance()) hasBalance++;
				// if a validators returns null that means the validation has failed
				if(signature.getBytes() == null) {
					if(validated) duration = System.currentTimeMillis()-start;
					validated = false;
				}
				sigma.add(signature);
			}
			t.setSigma(sigma);
			if(validated) {
				Util.log("Validation Successful");
				duration = System.currentTimeMillis()-start;
			}
			else Util.log("Validated failed.");
			

			if(mode == MALICIOUS) {
				malSuccess++;
			}
			testLog.logTransaction(validated,isAuthenticated,isSound,isCorrect,hasBalance,duration);
			return validated;
		} catch (Exception e) {
			e.printStackTrace();
			testLog.logTransaction(false,isAuthenticated,isSound,isCorrect,hasBalance,System.currentTimeMillis()-start);
			return false;
		}
	}
	/*
	 * This method is used to validate a block. It checks :
	 * 	1) Authenticity
	 * 	2) Consistency
	 * 	3) Authenticity and soundness of the transactions it contains.
	 */
	public SignedBytes PoV(Block blk) throws RemoteException {
		try {
			updateViewTable();
			Util.log("Validating block for " + blk.getOwner());
			boolean isAuth = isAuthenticated(blk);
			boolean isCons = isConsistent(blk);
			boolean val = isAuth && isCons;
			if(val == false)
				return new SignedBytes(null,isAuth,true,true,true);
			// iterate over transactions and check them one by one
			ArrayList<Transaction> ts = blk.getS();
			for(int i=0 ; i<ts.size() ; ++i) {
				if(!isAuthenticated(ts.get(i)) /*|| !isSound(ts.get(i))*/) {
					Util.log("Transaction inside block is not authentic");
					return new SignedBytes(null,isAuth,true,true,true);
				}
			}
			Util.log("Block Validation Successful");
			SignedBytes signedHash = new SignedBytes(digitalSignature.signString(blk.getH()).getBytes(),isAuth,true,true,true);
			return signedHash;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/*
	 * This method recieves a block and checks if:
	 * 1) its hash value is generated properly
	 * 2) checks if it contains the signature of its owner
	 */
	public boolean isAuthenticated(Block blk) throws RemoteException {
		try {
			
			long start = System.currentTimeMillis();
			
			// concatenate the string values of transactions 
			StringBuilder sb = new StringBuilder();
			for(int i=0 ; i<blk.getS().size(); ++i) {
				sb.append(blk.getS().get(i).toString());
			}
			// generate the hash value and then compare it with the block's
			String hash = hasher.getHash(blk.getPrev() + blk.getOwner() + sb.toString(),TRUNC);
			if(!hash.equals(blk.getH())) {
				Util.log("Hash value of block not generated properly");
				return false;
			}
			// get the sigma array of the block
			ArrayList<SignedBytes> blkSigma = blk.getSigma();
			// retrieve the public key of the owner of the block
			PublicKey ownerPublicKey = getOwnerPublicKey(blk.getOwner());
			boolean verified = false;
			if(ownerPublicKey == null)
				return false;
			// iterate over the sigma array looking for the signature of the owner
			for(int i=0 ; i<blkSigma.size(); ++i) {
				boolean is = digitalSignature.verifyString(hash, blkSigma.get(i), ownerPublicKey);
				if(is) verified = true;
			}
			if(verified == false) {
				Util.log("Block does not contain signature of Owner");
			}
			long end = System.currentTimeMillis();
			
			long time = end - start;
			
			return verified;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	/*
	 * A block is said to be consistent if its prev points to the current tail of the block, 
	 * so at the time of validation, validators should make sure that prev is still pointing
	 * to the tail of the blockchain, or otherwise if the tail was updated is should terminate
	 * the validation process.
	 */
	public boolean isConsistent(Block blk) throws RemoteException {
		try {
			long start = System.currentTimeMillis();
			
			Block lstBlk = getLatestBlock();	
			boolean res = blk.getPrev().equals(lstBlk.getH());
			if(res == false) {
				Util.log("Block not consistent");
			}
			
			long end = System.currentTimeMillis();
			
			long time = end - start;
			
			return res ;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	 * This method returns the validators of a given block
	 */
	public ArrayList<NodeInfo> getValidators(Block blk){
		try {
			// stores the final list of validators
			ArrayList<NodeInfo> validators = new ArrayList<>();
			// stores the address of the taken nodes so that we make sure we do not take a node twice
			HashMap<String,Integer> taken = new HashMap<>();   
			taken.put(address, 1);//To not take the node itself or any data node belonging to it.
			int count = 0, i = 0;
			// keep iterating until we get SIGNATURES_THRESHOLD number of validators
			while(count < SIGNATURES_THRESHOLD && i <= ALPHA) {
				String hash = blk.getPrev() + blk.getOwner() + blk.getS().toString() + i;
				int num = Integer.parseInt(hasher.getHash(hash,TRUNC),2);
				NodeInfo node = searchByNumID(num);
				i++;
				// if already taken or equals the owner's 			node, then keep iterating.
				if(taken.containsKey(node.getAddress()))continue;
				count++;
				taken.put(node.getAddress(), 1);
				validators.add(node);
			}
			return validators;
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	
	/*
	 * Checks the soundness, correctness and authenticity of a transaction using other methods.
	 * Checks if the owner of the transaction has enough balance to cover validation fees.
	 * returns null in case any of the tests fails, otherwise it signs the hashvalue of the transaction
	 * and sends the signed value to the owner.
	 */
	public SignedBytes PoV(Transaction t) throws RemoteException {
		boolean isAuth = false;
		boolean isCorrect = false;
		boolean isSound = false;
		boolean hasBalance = false;
		try {
			updateViewTable();
			Util.log("Validating Transaction for " + t.getOwner());
			try{
				isAuth = isAuthenticated(t);
			}catch(Exception e) {
				e.printStackTrace();
			}
			try{
				isCorrect = isCorrect(t);
			}catch(Exception e) {
				e.printStackTrace();
			}
			try {
				isSound = isSound(t);
			}catch(Exception e) {
				e.printStackTrace();
			}
			try{
				hasBalance = hasBalanceCompliance(t);
			}catch(Exception e) {
				e.printStackTrace();
			}
			boolean val = isAuth && isCorrect && isSound && hasBalance;
			if(val == false)
				return new SignedBytes(null, isAuth, isSound, isCorrect, hasBalance);
			Util.log("Transaction Validation Successful");
			SignedBytes signedHash = new SignedBytes(digitalSignature.signString(t.getH()).getBytes(),isAuth,isSound, isCorrect,hasBalance);
			return signedHash;
		} catch (Exception e) {
			e.printStackTrace();
			return new SignedBytes(null, isAuth, isSound, isCorrect, hasBalance);
		}
	}
	
	/*
	 * This method validated the soundness of a given transaction
	 * It checks if the given transaction is associated with a block
	 * which does not precede the block that contains the transaction's owner's 
	 * last transaction.
	 */
	public boolean isSound(Transaction t) {
		try {
			long start = System.currentTimeMillis();
			
			int prev = Integer.parseInt(t.getPrev(),2);
			// assuming that if a node is not in the view yet then this is the first
			// transaction;
			if(!view.containsKey(t.getOwner())) {
				view.put(t.getOwner(),prev);
				return true;
			}
			
			// get the hash value of the block that contains the owner's latest transaction			
			int blkNumID = view.get(t.getOwner());
			
			NodeInfo b1 = searchByNumID(prev);
			if(!(b1 instanceof Block)) {
				Util.log("isSound(): search for prev did not return a block: " + prev);
				Util.log("It returned numID: " + b1.getNameID());
				return false;
			}
			NodeInfo b2 = searchByNumID(blkNumID);
			if(!(b2 instanceof Block)) {
				Util.log("isSound(): search for latest block of owner did not return a block: " + prev);
				Util.log("It returned numID: " + b2.getNameID());
				return false;
			}
			
			Block prevBlk = (Block)b1;
			Block thisBlk = (Block)b2;
			
			int tIdx = prevBlk.getIndex();
			int bIdx = thisBlk.getIndex();

			Util.log("Index of prev: " + tIdx);
			Util.log("Index of latest: " + bIdx);
			if(tIdx <= bIdx) {
				Util.log("Transaction not sound");
				Util.log("Index of prev: " + tIdx);
				Util.log("Index of latest: " + bIdx);
			}else
				Util.log("Transaction is sound");
			long end = System.currentTimeMillis();
			
			long time = end - start;
			return tIdx > bIdx ;
		}catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	 * Returns true if both nodes are of same type (HONEST,HONEST) or (MALICIOUS,MALICIOUS)
	 * and returns false if both are of different types.
	 */
	public boolean isCorrect(Transaction t) {
		try {
			long start = System.currentTimeMillis();
			
			if(!viewMode.containsKey(t.getOwner())) {
				LightChainRMIInterface rmi = getLightChainRMI(t.getAddress());
				int ownerMode = rmi.getMode();
				viewMode.put(t.getOwner(), ownerMode);
				return ownerMode == mode;
			}
			int ownerMode = viewMode.get(t.getOwner());
			if(ownerMode != mode) {
				Util.log("Transaction not correct");
			}
			
			long end = System.currentTimeMillis();
			
			long time = end - start;
			
			return ownerMode == mode;
		} catch (RemoteException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	 * This method receives a transaction and verifies two things:
	 * 1- The hashvalue of the transaction was generated according to the correct equation
	 * 2- The sigma of the transaction contains the transaction's owner signed value of the hashvalue
	 */
	public boolean isAuthenticated(Transaction t) throws RemoteException {
		try {
			long start = System.currentTimeMillis();
			
			// generate the hash using the equation to check if it was generated correctly
			String hash = hasher.getHash(t.getPrev()+t.getOwner()+t.getCont(),TRUNC);
			// return false if it was not generated properly
			if(!hash.equals(t.getH())) {
				Util.log("Transaction hash value not generated properly");
				return false;
			}
			// now get the sigma array from transaction and iterate over signatures it contains
			ArrayList<SignedBytes> tSigma = t.getSigma();
			PublicKey ownerPublicKey = getOwnerPublicKey(t.getOwner());
			boolean verified = false;
			if(ownerPublicKey == null)
				return false;
			for(int i=0 ; i<tSigma.size(); ++i) {
				// if we find one signature which belongs to the owner then we set verified to true
				boolean is = digitalSignature.verifyString(hash, tSigma.get(i), ownerPublicKey);
				if(is) verified = true;
			}
			if(verified == false) {
				Util.log("Transaction does not contain signature of owner");
			}
			
			long end = System.currentTimeMillis();
			
			long time = end - start;
			
			return verified;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	/*
	 * Checks if the owner of the transaction has enough balance to cover the fees
	 * of validation
	 */
	public boolean hasBalanceCompliance(Transaction t) {
		try {
			long start = System.currentTimeMillis();
			
			if(!viewBalance.containsKey(t.getOwner())) {
				viewBalance.put(t.getOwner(),INITIAL_BALANCE);
				return true;
			}
			int ownerBalance = viewBalance.get(t.getOwner());
			
			long end = System.currentTimeMillis();
			
			long time = end - start;
			
			return ownerBalance >= VALIDATION_FEES;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	 * This method returns the validators of a given transaction
	 */
	public ArrayList<NodeInfo> getValidators(Transaction t){
		try {
			// stores the validators to be returned
			ArrayList<NodeInfo> validators = new ArrayList<>(); 
			// used as a lookup to check if a node has been already added to the validators array
			// because the search might return the same node more than once
			// so in order to avoid this case, when we find an already added node, 
			// we repeat the search
			HashMap<String,Integer> taken = new HashMap<>(); 
			taken.put(address, 1);//To not take the node itself or any data node belonging to it.
			int count = 0 , i = 0;
			while(count < SIGNATURES_THRESHOLD && i <= ALPHA) { // terminates when we get the required number of validators
				String hash = t.getPrev() + t.getOwner() + t.getCont() + i ;
				int num = Integer.parseInt(hasher.getHash(hash,TRUNC),2);
				NodeInfo node = searchByNumID(num);
				i++;
				if(taken.containsKey(node.getAddress()))continue;
				count++;
				taken.put(node.getAddress(), 1);
				validators.add(node);
			}
			return validators;
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/*
	 * This method recieves the numID of an owner of a transaction or a block
	 * and first verifies of the given public key truly belongs to the owner
	 * by hashing the provided public key and comparing it with the given numID
	 * if the test fails it prints it to console and return null.
	 * otherwise it returns the public key of the owner.
	 */
	public PublicKey getOwnerPublicKey(int num) throws RemoteException {
		try {
			// find owner from the network
			NodeInfo owner = searchByNumID(num);
			
			if(owner.getNumID() != num) {
				Util.log("GetOwnerPublicKey: no node was found with given numID");
				Util.log("Given numID: " + num);
				Util.log("Found numID: " + owner.getNumID());
				return null;
			}
			
			// Contact the owner through RMI
			LightChainRMIInterface ownerRMI = getLightChainRMI(owner.getAddress());
			// get the owner'r Public key through RMI
			PublicKey pk = ownerRMI.getPublicKey();
			// Hash the public key and store the hash value as int
			int hashedKey = Integer.parseInt(hasher.getHash(pk.getEncoded(),TRUNC),2);
			// if hashedKey is not equal to the provided numID, then there is a problem
			// and it is printed to the console
			if(hashedKey != num) {
				Util.log("GetOwnerPublicKey: given numID does not match with hash value of public key.");
				return null;
			}
			return pk;
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static LightChainRMIInterface getLightChainRMI(String adrs) {
		if(Util.validateIP(adrs)) {
			if(adrs.equalsIgnoreCase(address)) return lightChainNode;
			try {
				return (LightChainRMIInterface)Naming.lookup("//"+adrs+"/RMIImpl");
			}catch(Exception e) {
				Util.log("Exception while attempting to lookup RMI located at address: "+adrs);
				e.printStackTrace();
			}
		} else {
			Util.log("Error in looking up RMI. Address: "+ adrs + " is not a valid address.");
		}
		return null;
	}
	
	public PublicKey getPublicKey() throws RemoteException {
		return digitalSignature.getPublicKey();
	}
	
	public ArrayList<Transaction> getTransactions() throws RemoteException{
		return transactions;
	}
	
	public int getBalance() {
		return balance;
	}
	
	public String numToName(int num) {
		String name = Integer.toBinaryString(num);
		while(name.length() < TRUNC) {
			name = "0" + name;
		}
		return name;
	}
	
	public int getMode() throws RemoteException{
		return mode;
	}
	
	// for Testing:
	
	public void insertGen() throws RemoteException{
		StringBuilder st = new StringBuilder();
		for(int i=0;i<TRUNC;i++) {
			st.append("0");
		}
		String prev = st.toString();
		int index = 0;
		Block b = new Block(prev,getNumID(),getAddress(),index);
		insert(new NodeInfo(getAddress(),ZERO_ID,b.getH()));
		insert(b);
	}
	
	public void put(Transaction t) throws RemoteException{
		transactions.add(t);
		insert(t);
	}
	public void put(Block t) throws RemoteException{
		blocks.add(t);
		insert(t);
	}
	
	private void createNewTransaction(String cont) {
		try {
			Block lstBlk = getLatestBlock();
			Util.log("The prev found is : " + lstBlk.getNumID());
			Transaction t = new Transaction(lstBlk.getH(), getNumID() ,cont, getAddress());
			boolean verified = validate(t);
			if(verified == false) {
				Util.log("Transaction validation Failed");
				return ;
			}
			Util.log("Added transaction with nameID " + lstBlk.getH());
			t.setAddress(getAddress());
			transactions.add(t);
			insert(t);
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public TestingLog startSim(int numTransactions, int pace) throws RemoteException {
		testLog = new TestingLog(mode == MALICIOUS);
		Random rnd = new Random();
		try {
			for(int i=0;i<numTransactions;i++) {
				Thread.sleep(rnd.nextInt(1000 * pace)/2);//wait for (pace +- 10 seconds)/2 
				createNewTransaction(System.currentTimeMillis()+i+""+rnd.nextDouble());
				Thread.sleep(rnd.nextInt(1000 * pace)/2);//wait for (pace +- 10 seconds)/2 
				updateViewTable();
				if(i%1 == 0) {
					Thread.sleep(rnd.nextInt(rnd.nextInt(4000)));
					viewUpdate();
				}
				if(i%5==0) System.out.println(100.0*i / numTransactions + "% done.");
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return testLog;
	}
	
	public TestingLog getTestLog() throws RemoteException {
		return testLog;
	}
	
	public void shutDown() throws RemoteException{
		System.exit(0);
	}
	

	
}
