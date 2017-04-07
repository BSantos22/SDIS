package Channel;


import Header.Field;
import Header.Type;
import Server.Peer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.util.HashMap;

public class MDRChannel extends Channel {

    public MDRChannel(int port, String address){
        super(port, address);
    }

    public void handle(DatagramPacket packet){
        super.handle(packet);
        if(Integer.parseInt(packetHeader[Field.senderId]) == Peer.peerId.id)
            return;
        String type = packetHeader[Field.type];
        switch (type){
            case Type.chunk:
                handleCHUNK(packetHeader, packetBody);
        }
    }

    private void handleCHUNK(String[] packetHeader, byte[]body){
        //TODO
        //verify header for fileID and chunkNo
        String thread = "RESTORE " + packetHeader[Field.fileId];
        String key = packetHeader[Field.fileId] + packetHeader[Field.chunkNo];
        if(Peer.threadExists(thread)) {
            //put byte array to hashmap and wake up restore thread
            Peer.chunks.put(key, body);
            Peer.wakeThread(thread);
        } else{
            Peer.chunks.put(key, new byte[0]);
        }
    }

    public byte[] getChunk(String key) {
        return Peer.chunks.get(key);
    }

    public void removeChunk(String fileId, int chunkNo) {
        Peer.chunks.remove(fileId + chunkNo);
    }
}
