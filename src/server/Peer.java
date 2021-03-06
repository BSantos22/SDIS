package server;

import channel.*;

import chunks.ChunkId;
import chunks.ChunkInfo;
import client.Service;
import file.Disk;
import file.FileInfo;
import org.omg.CORBA.INTERNAL;
import subProtocols.Backup;
import subProtocols.Delete;
import subProtocols.Restore;
import subProtocols.Reclaim;
import utils.Utils;

import java.io.*;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;

public class Peer implements RMIService {

    private static MulticastSocket socket;

    //stored messages received by peers that have the corresponding chunk
    private static HashMap<ChunkId, ChunkInfo> replies = new HashMap<>();

    //saves fileId and thread of the subprotocol process
    //when creating thread puts it into this hashmap
    private static HashMap<String, Thread> protocols = new HashMap<>();

    //saves files that have been backed up from this peer
    private static HashMap<String, FileInfo> restorations = new HashMap<>();

    private static String remObj;

    public static Channel mcChannel;
    public static Channel mdbChannel;
    public static Channel mdrChannel;

    public static PeerId peerId;

    public static void main(String[] args) {

        try {
            socket = new MulticastSocket();
        }catch(IOException err){
            err.printStackTrace();
        }

        if(args.length != 9){
            printUsage();
            return;
        }

        Disk.analyzeUsedSpace();

        init(args);
        initRMI();
        replies = new HashMap<>();

        Thread mcThread = new Thread(mcChannel);
        Thread mdbThread = new Thread(mdbChannel);
        Thread mdrThread = new Thread(mdrChannel);
        mcThread.start();
        mdbThread.start();
        mdrThread.start();
        loadFile();
        loadInitiator();

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

    public synchronized static ChunkInfo getChunkInfo(ChunkId id) {
        return replies.get(id);
    }

    public synchronized static void addReply(ChunkId id, ChunkInfo info) {
        replies.put(id, info);
        saveRepliesToFile();
    }

    public static void deleteReply(ChunkId id) {
        replies.remove(id);
        saveRepliesToFile();
    }

    private static void saveRepliesToFile() {
        BufferedWriter out;
        try {
            FileWriter fstream = new FileWriter(Utils.INDEXFILE);
            out = new BufferedWriter(fstream);

            for (ChunkId key: replies.keySet()) {
                out.write(key.getFileId() + " " + ((Integer) key.getChunkNo()).toString() + "\n");
                out.write(replies.get(key).getReplDegree() + " " + replies.get(key).getConfirmations() + "\n");
            }

            out.close();
        }
        catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void saveInitiator(){
        BufferedWriter out;
        try {
            FileWriter fstream = new FileWriter(Utils.INITIATOR);
            out = new BufferedWriter(fstream);

            for (String key: restorations.keySet()) {
                FileInfo fileInfo = restorations.get(key);
                out.write(key + "\n" + fileInfo.getFileId() + "\n" + fileInfo.getDesiredReplication() + "\n");
                for (int replications:fileInfo.getChunksReplicated()) {
                    out.write(replications + " ");
                }
                out.newLine();
            }

            out.close();
        }
        catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void loadInitiator(){
        BufferedReader buffer;
        try {
            buffer = new BufferedReader(new FileReader(Utils.INITIATOR));
        }catch(FileNotFoundException err){
            return;
        }
        String info, fileName, fileId;
        String[] splitInfo;
        int replications;
        do{
            try {
                fileName = buffer.readLine();
                if (fileName == null) return;

                fileId = buffer.readLine();

                info = buffer.readLine();
                replications = Integer.parseInt(info);
                FileInfo fileInfo = new FileInfo(fileId, replications);
                info = buffer.readLine();
                if(info.isEmpty()){
                    if(peerId.getVersion().equals(Utils.SPACE_ENHANCE) || peerId.getVersion().equals(Utils.ALL_ENHANCE)){
                        new Thread(new Backup(fileName, replications)).start();
                    }
                } else {

                    splitInfo = info.split(" ");

                    for (String str : splitInfo) {
                        fileInfo.storeReplication(Integer.parseInt(str));
                    }
                    restorations.put(fileName, fileInfo);

                    if(!Utils.correctBackup(fileName, splitInfo.length)){
                        fileInfo.removeLast();
                        restorations.put(fileName, fileInfo);
                        new Thread(new Backup(fileName, replications, fileId, splitInfo.length-1)).start();
                    }
                }

            }catch(IOException err){
                err.printStackTrace();
                return;
            }
        }while(true);
    }

    private static void loadFile(){
        BufferedReader buffer;
        try {
            buffer = new BufferedReader(new FileReader(Utils.INDEXFILE));
        }catch(FileNotFoundException err){
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

    public static void savePath(String filePath, String fileId, int replications){
        restorations.put(filePath, new FileInfo(fileId, replications));
        saveInitiator();
    }

    public static void deletePath(String filePath){
        restorations.remove(filePath);
        saveInitiator();
    }

    public static void saveStores(String filePath, int repliesReceived){
        FileInfo info = restorations.get(filePath);
        info.storeReplication(repliesReceived);
        restorations.put(filePath, info);
        saveInitiator();
    }

    public static HashMap<String, FileInfo> getRestorations(){
        return restorations;
    }

    public static void addProtocol(String name, String identifier, Thread t) {
        protocols.put(name + " " + identifier, t);
    }

    public static void deleteProtocol(String type, String identifier){
        protocols.remove(type + " " + identifier);
    }

    private static void printUsage() {
        System.out.println("Wrong number of arguments");
        System.out.println("Usage: java server.Peer version id RmiName mcAddress mcPort mdbAddress mdbPort mdrAddress mdrPort");
    }

    //is it useful to add threads to hashmap since you cannot interrupt sleep during backup?
    public boolean backup(String file, int replDegree) {
        Backup backup = new Backup(file, replDegree);
        Thread t = new Thread(backup);
        addProtocol(Service.backup, backup.getFileId(), t);
        t.start();
        return true;
    }

    public boolean restore(String file) {
        Restore restore = new Restore(file);
        Thread t = new Thread(restore);
        addProtocol(Service.restore, restore.getFileId(), t);
        t.start();
        return true;
    }

    public boolean delete(String file) {
        Delete delete = new Delete(file);
        Thread t = new Thread(delete);
        addProtocol(Service.delete, delete.getFileId(), t);
        t.start();
        return true;
    }

    public boolean reclaim(int space) {
        Reclaim reclaim = new Reclaim(space);
        Thread t = new Thread(reclaim);
        addProtocol(Service.reclaim, String.valueOf(space), t);
        t.start();
        return true;
    }

    public boolean space(int space){
        Disk.setMaxSpace(space);
        if(Disk.getAvailableSpace() < 0){
            reclaim(Disk.getAvailableSpace()*-1);
        }
        return true;
    }

    public String state(){
        String state = "Files backed up from this peer:\n";
        for(HashMap.Entry<String, FileInfo> info: restorations.entrySet()){
            state += "FilePath: " + info.getKey() + "\n";
            state += info.getValue();
        }

        state += "\n\nChunks saved in this peer:\n";
        for(HashMap.Entry<ChunkId, ChunkInfo> info: replies.entrySet()){
            state += "FilePath: " + info.getKey() + "\n";
            state += "Size: " + Disk.getChunkSize(info.getKey()) + "\n";
            state += info.getValue();
        }

        state += "\nTotal disk space is: " + Disk.getMaxSpace();
        state += "\nUsed disk space is: " + Disk.getUsedSpace();
        state += "\nAvailable disk space is: " + Disk.getAvailableSpace() + "\n";
        return state;
    }
}
