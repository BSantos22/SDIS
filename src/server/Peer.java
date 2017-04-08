package server;

import channel.*;

import chunks.ChunkId;
import chunks.ChunkInfo;
import file.FileInfo;
import subProtocols.Backup;
import subProtocols.Delete;
import subProtocols.Restore;
import subProtocols.Reclaim;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

public class Peer implements RMIService {

	private static final String INDEXFILE = "index.txt";

    private static MulticastSocket socket;

    private static HashMap<ChunkId, ChunkInfo> replies = new HashMap<>();

    //TODO
    //saves fileId and thread of the subprotocol process
    //when creating thread puts it into this hashmap
    private static HashMap<String, Thread> protocols = new HashMap<>();

    private static HashMap<String, FileInfo> restorations = new HashMap<>();

    private static String remObj;

    public static Channel mcChannel;
    public static Channel mdbChannel;
    public static Channel mdrChannel;

    public static PeerId peerId;

    public static Thread mcThread;
    public static Thread mdbThread;
    public static Thread mdrThread;

    public static void main(String[] args) {

        if(args.length != 9){
            printUsage();
            return;
        } else{
            init(args);
            loadFile();
        }
        try {
            socket = new MulticastSocket();
        }catch(IOException err){
            err.printStackTrace();
        }
        initRMI();
        replies = new HashMap<>();

        mcThread = new Thread(mcChannel);
        mdbThread = new Thread(mdbChannel);
        mdrThread = new Thread(mdrChannel);
        mcThread.start();
        mdbThread.start();
        mdrThread.start();
        System.out.println("Initialized Peer " + args[1]);

    }


    private static void init(String[] args){
        mcChannel = new MCChannel(Integer.parseInt(args[4]), args[3]);
        mdbChannel = new MDBChannel(Integer.parseInt(args[6]), args[5]);
        mdrChannel = new MDRChannel(Integer.parseInt(args[8]), args[7]);

        peerId = new PeerId(Integer.parseInt(args[1]), args[0]);
        remObj = args[2];
    }

    private static void initRMI(){
        try {
            Peer peer = new Peer();
            RMIService stub = (RMIService) UnicastRemoteObject.exportObject(peer, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(remObj, stub);
            System.out.println("Peer bound to remote object name: " + remObj);
        }
        catch(Exception e) {
            System.err.println("RMIService exception");
            e.printStackTrace();
        }
    }

    public static void sendToChannel(byte[] message, Channel channel){

        DatagramPacket packet = new DatagramPacket(message, message.length, channel.address, channel.port);
        try {
            socket.send(packet);
        }catch(IOException err){
            err.printStackTrace();
        }
    }

    public static void wakeThread(String key){
        Thread t = protocols.get(key);
        t.interrupt();
    }

    public static boolean threadExists(String key){
        return protocols.get(key) != null;
    }

    public static HashMap<ChunkId, ChunkInfo> getReplies() {
		return replies;
	}

	public static ChunkInfo getChunkInfo(ChunkId id) {
		return replies.get(id);
	}

	public static void addReply(ChunkId id, ChunkInfo info) {
		replies.put(id, info);
		saveRepliesToFile();
	}
	
	public static void deleteReply(ChunkId id) {
		replies.remove(id);
		saveRepliesToFile();
	}
	
	private static void saveRepliesToFile() {
		BufferedWriter out = null;
		try {
			FileWriter fstream = new FileWriter(INDEXFILE);
			out = new BufferedWriter(fstream);
			
			for (ChunkId key: replies.keySet()) {
				out.write(key.getFileId() + " " + ((Integer) key.getChunkNo()).toString() + "\n");
				out.write(replies.get(key).replDegree + " " + replies.get(key).confirmations + "\n");
			}

			out.close();
		}
		catch (IOException e) {
			System.err.println("Error: " + e.getMessage());
		}
	}

    private static void loadFile(){
        BufferedReader buffer;
        try {
             buffer = new BufferedReader(new FileReader(INDEXFILE));
        }catch(FileNotFoundException err){
            err.printStackTrace();
            return;
        }
        String info, fileName;
        String[] splitInfo, splitName;
        ChunkInfo chunkInfo;
        ChunkId chunkId;
        do{
            try {
                fileName = buffer.readLine();
                if (fileName == null) return;

                info = buffer.readLine();
                if(info == null) return;
                splitName = fileName.split(" ");
                splitInfo = info.split(" ");
                chunkId = new ChunkId(splitName[0], Integer.parseInt(splitName[1]));
                chunkInfo = new ChunkInfo(Integer.parseInt(splitInfo[0]), Integer.parseInt(splitInfo[1]));
                replies.put(chunkId, chunkInfo);

            }catch(IOException err){
                err.printStackTrace();
                return;
            }
        }while(true);
    }

    public static void savePath(String filePath, String fileId, int totalChunks){
        restorations.put(filePath, new FileInfo(fileId, totalChunks));
    }

    public static HashMap<String, FileInfo> getRestorations(){
        return restorations;
    }


    private static void printUsage() {
        System.out.println("Wrong number of arguments");
        System.out.println("Usage: java Server.Peer version id RmiName mcAddress mcPort mdbAddress mdbPort mdrAddress mdrPort");
    }

    //TODO
    //is it useful to add threads to hashmap since you cannot interrupt sleep during backup?
    public boolean backup(String file, int replDegree) {
        Backup backup = new Backup(file, replDegree, socket, mdbChannel.address, mdbChannel.port);
        Thread t = new Thread(backup);
        protocols.put("BACKUP " + backup.getFileId(), t);
        t.start();
    	return true;
    }
    
    public boolean restore(String file) {
        Restore restore = new Restore(file);
        Thread t = new Thread(restore);
        protocols.put("RESTORE " + restore.getFileId(), t);
        t.start();
        return true;
    }
    
    public boolean delete(String file) {
        Delete delete = new Delete(file);
        Thread t = new Thread(delete);
        protocols.put("DELETE " + delete.getFileId(), t);
        t.start();
    	return true;
    }
    
    public boolean reclaim(int space) {
    	Reclaim reclaim = new Reclaim(space);
    	Thread t = new Thread(reclaim);
    	protocols.put("RECLAIM " + reclaim.getFileId(), t);
        t.start();
    	return true;
    }
}