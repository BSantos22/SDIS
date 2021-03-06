package file;

import java.util.ArrayList;

public class FileInfo {

    private String fileId;
    private int desiredReplication;
    private ArrayList<Integer> chunksReplicated = new ArrayList<>();

    public FileInfo(String fileId, int desiredReplication){
        this.fileId = fileId;
        this.desiredReplication = desiredReplication;
    }

    public void storeReplication(int storesReceived){
        chunksReplicated.add(storesReceived);
    }

    public void removedReplication(int chunkNo){
        chunksReplicated.set(chunkNo, chunksReplicated.get(chunkNo)-1);
    }

    public void removeLast(){
        chunksReplicated.remove(chunksReplicated.size() - 1);
    }

    public boolean acceptableReplications(int chunkNo){
        return chunksReplicated.get(chunkNo) >= desiredReplication;
    }

    public int getDesiredReplication(){
        return desiredReplication;
    }

    public String getFileId(){
        return fileId;
    }

    public ArrayList<Integer> getChunksReplicated(){
        return chunksReplicated;
    }

    @Override
    public String toString(){
        String header = "FileId: " + fileId +
                "\nDesired replication degree: " + desiredReplication +
                "\nChunks:\n";
        String chunks = "";
        for (int i = 0; i < chunksReplicated.size(); i++) {
            chunks += "     No: " + (i+1) + " has been replicated " + chunksReplicated.get(i) + " time(s)\n";
        }
        return header + chunks;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        FileInfo f = (FileInfo) obj;
        return this.fileId.equals(f.fileId);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + 32;
        result = prime * result + chunksReplicated.size();
        return result;
    }

}
