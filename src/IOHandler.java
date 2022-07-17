import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IOHandler {
    private final String treePath;
    private final String dataPath;
    private int nextFreeTreeAddress = 0;
    private int nextFreeDataAddress = 0;

    private int nrOfPageWrites = 0;
    private int nrOfPageReads = 0;

    private int nrOfDataWrites = 0;
    private int nrOfDataReads = 0;

    private int bytesCounter = 0;

    private static final int MAX_PAGE_SIZE = Page.MAX_RECORD_COUNT * (Integer.BYTES + Integer.BYTES) + (Page.MAX_RECORD_COUNT+1) * Integer.BYTES + Integer.BYTES; // 4 klucze i adresy rekordow + 5 wskaznikow na dane + adres rodzica
    private static final int BLOCK_SIZE = 64;
    private final List<Page> pagesToWrite = new ArrayList<>();

    public IOHandler(String treePath, String dataPath) throws IOException {
        this.treePath = treePath;
        this.dataPath = dataPath;
        new FileOutputStream(treePath, false).flush(); // Reset file
        new FileOutputStream(dataPath, false).flush(); // Reset file
    }

    public void writeData(Record added) throws IOException {
        RandomAccessFile dataStream = new RandomAccessFile(this.dataPath, "rw");
        dataStream.skipBytes(nextFreeDataAddress);
        added.setMainFileOffset(nextFreeDataAddress);

        dataStream.writeInt(added.getSet().size());
        nextFreeDataAddress += Integer.BYTES;
        bytesCounter += 4;
        for(int i = 0; i < added.getSet().size(); i++) {
            dataStream.writeFloat(added.getSet().get(i));
            nextFreeDataAddress += Float.BYTES;
            bytesCounter += 4;
        }
        if(bytesCounter >= BLOCK_SIZE) {
            nrOfDataWrites++;
            bytesCounter = 0;
        }
        dataStream.close();
    }

    public Record readData(Integer address) throws IOException{
        RandomAccessFile dataStream = new RandomAccessFile(this.dataPath, "rw");
        Record record = new Record();
        dataStream.skipBytes(address);
        record.setMainFileOffset(address);

        int count = dataStream.readInt();
        List<Float> data = new ArrayList<>();
        for(int i = 0; i < count; i++) {
            data.add(dataStream.readFloat());
        }
        record.setSet(data);
        nrOfDataReads++;
        return record;
    }

    public void allocateAddress(Page page) {
        page.setMyAddressInTreeFile(nextFreeTreeAddress);
        nextFreeTreeAddress += MAX_PAGE_SIZE;
    }

    public void writePage(Page page) throws IOException {
        RandomAccessFile treeStream = new RandomAccessFile(this.treePath, "rw");

        treeStream.skipBytes(page.getMyAddressInTreeFile());
        treeStream.writeInt(page.getParentAddress());

        for(int i = 0; i < page.getKeys().size(); i++) {
            treeStream.writeInt(page.getKeys().get(i));
            treeStream.writeInt(page.getRecordAddresses().get(i));
        }
        for(int i = page.getKeys().size(); i < Page.MAX_RECORD_COUNT; i++) {
            treeStream.writeInt(-1);
            treeStream.writeInt(-1);
        }
        for(int i = 0; i < Page.MAX_RECORD_COUNT + 1; i++) {
            treeStream.writeInt(page.getChildPagesAddresses().get(i));
        }
        nrOfPageWrites++;
    }

    public Page readPage(int pageAddress) throws IOException {
        RandomAccessFile treeStream = new RandomAccessFile(this.treePath, "rw");
        int parentAddress;
        try {
            treeStream.skipBytes(pageAddress);
            parentAddress = treeStream.readInt();
        } catch (EOFException eof) {
            return null;
        }

        Page page = new Page(this, parentAddress);
        page.setMyAddressInTreeFile(pageAddress);

        for(int i = 0; i < Page.MAX_RECORD_COUNT; i++) {
            int key = treeStream.readInt();
            int address = treeStream.readInt();
            if(key != -1) {
                page.getKeys().add(key);
                page.getRecordAddresses().add(address);
            }
        }
        for(int i = 0; i < Page.MAX_RECORD_COUNT + 1; i++) {
            page.getChildPagesAddresses().set(i, treeStream.readInt());
        }

        nrOfPageReads++;
        return page;
    }

    public void queueWrite(Page page)
    {
        pagesToWrite.add(page);
    }

    public void writeQueued() throws IOException
    {
        Set<Integer> addresses = new HashSet<>();

        for (Page page : pagesToWrite) {
            if (!addresses.contains(page.getMyAddressInTreeFile())) {
                addresses.add(page.getMyAddressInTreeFile());
                try {
                    writePage(page);
                } catch (NullPointerException ex) {
                    System.out.println();
                }
            }
        }

        pagesToWrite.clear();
    }
    public int getAllDiskOperations() {
        return nrOfDataReads + nrOfDataWrites + nrOfPageWrites + nrOfPageReads;
    }
}
