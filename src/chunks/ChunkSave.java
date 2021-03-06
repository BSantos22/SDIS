package chunks;


import channel.Channel;
import file.Disk;
import header.Type;
import server.Peer;
import subProtocols.Reclaim;
import utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class ChunkSave implements Runnable {
    private String fileId;
    private int chunkNo;
    private byte[] body;
    private int replication;

    public ChunkSave(String fileId, int chunkNo, int replication, byte[] body){
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.replication = replication;
        this.body = body;
    }

    public void run() {
            createFolders();
            int stores = 0;
            ChunkInfo chunkInfo = new ChunkInfo(replication);
            ChunkId chunkId = new ChunkId(fileId, chunkNo);

            Peer.addReply(chunkId, chunkInfo);
            Utils.waitTime();
            if(Peer.peerId.getVersion().equals(Utils.BACKUP_ENHANCE) || Peer.peerId.getVersion().equals(Utils.ALL_ENHANCE)) {
                stores = Peer.mcChannel.getStoredMessages(fileId, chunkNo);
                if(stores >= replication) {
                    Peer.deleteReply(chunkId);
                    return;
                }
            }
            writeChunk();

            //create entry in hashmap of replies
            Peer.addReply(chunkId, new ChunkInfo(replication, stores + 1));
            String header = Channel.createHeader(Type.stored, fileId, chunkNo, -1);
            Peer.sendToChannel(header.getBytes(), Peer.mcChannel);
            System.out.println("Created backup for fileId: " + fileId + " and chunkNo: " + chunkNo);
    }

    private void createFolders(){
        File file = new File(Utils.storage);
        if(!file.exists())
            file.mkdir();
        file = new File(Utils.storage + "/" + fileId);
        if(!file.exists())
            file.mkdir();
    }

    private void writeChunk(){
        try {
            RandomAccessFile r = new RandomAccessFile(Utils.storage + "/" + fileId + "/" + chunkNo, "rw");
            r.write(body);
            r.close();
            Disk.occupy(body.length);
            if (Disk.getAvailableSpace() < 0) {
                new Thread(new Reclaim(Disk.getAvailableSpace() * -1)).start();
            }
        }catch (IOException err){
            err.printStackTrace();
        }
    }
}
