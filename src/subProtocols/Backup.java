package subProtocols;

import channel.Channel;
import header.Type;
import server.Peer;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.security.MessageDigest;
import java.util.Arrays;

public class Backup extends SubProtocol implements Runnable {

    private final int MAX_SIZE = 64000;
    private final int SEND_REPEAT = 5;
    private int replDegree;
    RandomAccessFile in;
    MulticastSocket socket;
    InetAddress address;
    int port;

    public Backup(String filePath, int replDegree, MulticastSocket socket, InetAddress address, int port){
        super(filePath);
        fileId = getFileId(filePath);
        this.replDegree = replDegree;
        this.socket = socket;
        this.address = address;
        this.port = port;
        this.filePath = filePath;
        try {
            in = new RandomAccessFile(filePath, "r");
        } catch (IOException err){
            System.err.println("File not found");
        }
    }

    public void run(){

        int i = 0, repeats, confirmations;
        int numChunks = 0, timeout = 500; //in miliseconds
        byte[] buf, body = new byte[MAX_SIZE];
        do {
            //read bytes from file
            try {
                i = in.read(body);
                numChunks++;
            } catch (IOException err) {
                err.printStackTrace();
            }

            if(i != 64000 && i != -1){
                body = Arrays.copyOfRange(body, 0, i);
            }

            buf = createPacket(body, numChunks);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
            repeats = 0;
            do{
                repeats++;
                timeout *= 2;
                try{
                    Peer.mcChannel.startStoredCount(fileId, numChunks, replDegree);
                    socket.send(packet);
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException err){
                        err.printStackTrace();
                    }
                }catch (IOException err){
                    err.printStackTrace();
                }
                confirmations = Peer.mcChannel.getStoredMessages(fileId, numChunks);

            } while(confirmations < replDegree && repeats < SEND_REPEAT);
            if(confirmations >= replDegree){
                timeout = 500;
                System.out.println("Stored chunk " + numChunks + " with acceptable replication degree: " + confirmations);
            } else{
                System.out.println("No answer");
            }
        } while(i == 64000);

        System.out.println("Backup completed");
        Peer.savePath(filePath, fileId, numChunks);
    }

    private byte[] createPacket(byte[]body, int numChunks){
        String header = Channel.createHeader(Type.putchunk, fileId, numChunks, replDegree);
        byte[] headerArray = header.getBytes();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(headerArray);
            outputStream.write(body);
        } catch(IOException err){
            err.printStackTrace();
        }
        return outputStream.toByteArray();
    }

    private static String getFileId(String path) {
        File file = new File(path);
        String base = path + file.lastModified() + file.length();

        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch(Exception ex){
            throw new RuntimeException(ex);
        }
    }
}