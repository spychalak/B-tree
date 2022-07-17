import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Page {
    public static final int MIN_RECORD_COUNT = 2; // = D (min M)
    public static final int MAX_RECORD_COUNT = MIN_RECORD_COUNT * 2; // 2 * D
    public static final int NO_CHILD = -1;

    private final int parentAddress;
    private Integer myAddressInTreeFile = null;
    private final List<Integer> childPagesAddresses = new ArrayList<>();

    private final List<Integer> keys = new ArrayList<>();
    private final List<Integer> recordAddresses = new ArrayList<>();

    private final IOHandler handler;
    private final TreeManager manager;

    public Page(IOHandler handler, int parentAddress) {
        this.parentAddress = parentAddress;
        for (int i = 0; i < MAX_RECORD_COUNT + 1; i++) {
            this.childPagesAddresses.add(NO_CHILD);
        }
        this.handler = handler;
        this.manager = new TreeManager(handler);
    }

    void addRecord(Record added, int key) throws IOException {
        if (keys.isEmpty() || key > keys.get(keys.size() - 1)) {
            keys.add(key);
            recordAddresses.add(added.getMainFileOffset());
        } else {
            for (int i = 0; i < keys.size(); i++) {
                if (key < keys.get(i)) {
                    keys.add(i, key);
                    recordAddresses.add(i, added.getMainFileOffset());
                    break;
                }
            }
        }

        if (this.getKeys().size() > MAX_RECORD_COUNT) {
            manager.handleOverflow(this);
        }

        handler.queueWrite(this);
    }

    void compensate(boolean left, Page brother, Page parent) throws IOException {
        int movedKeyIdxInBrother = left ? brother.getKeys().size() : 0;
        int movedKeyIdxInParent = parent.idxOfChildAddress(myAddressInTreeFile) - (left ? 1 : 0);

        brother.getKeys().add(movedKeyIdxInBrother, parent.getKeys().get(movedKeyIdxInParent));
        brother.getRecordAddresses().add(movedKeyIdxInBrother, parent.getRecordAddresses().get(movedKeyIdxInParent));

        handler.writePage(brother);

        int movedKeyIdxMe = left ? 0 : getKeys().size() - 1;
        parent.getKeys().set(movedKeyIdxInParent, getKeys().get(movedKeyIdxMe));
        parent.getRecordAddresses().set(movedKeyIdxInParent, getRecordAddresses().get(movedKeyIdxMe));

        handler.writePage(parent);

        getKeys().remove(movedKeyIdxMe);
        getRecordAddresses().remove(movedKeyIdxMe);

        handler.writePage(this);
    }

    boolean isHungry() {
        return this.getKeys().size() != MAX_RECORD_COUNT;
    }

    Page getBrother(Page parent, boolean left) throws IOException {
        int indexOfMyAddressInParentChildAddresses = parent.idxOfChildAddress(myAddressInTreeFile);
        int brotherIndexInParentChildAddresses = indexOfMyAddressInParentChildAddresses + (left ? -1 : +1);
        if (brotherIndexInParentChildAddresses < 0 || brotherIndexInParentChildAddresses > MAX_RECORD_COUNT) {
            return null;
        }
        int theBrotherAddress = parent.childPagesAddresses.get(brotherIndexInParentChildAddresses);
        if (theBrotherAddress != NO_CHILD) {
            return handler.readPage(theBrotherAddress);
        }
        return null;
    }

    private int idxOfChildAddress(int childAddress) {
        return childPagesAddresses.lastIndexOf(childAddress);
    }

    boolean isLeaf() {
        for (int i = 0; i < this.getKeys().size() + 1; i++) {
            if (this.childPagesAddresses.get(i) != NO_CHILD) {
                return false;
            }
        }
        return true;
    }

    void splitChild() throws IOException {

        Page newBrother = new Page(handler, parentAddress);
        handler.allocateAddress(newBrother);
        int middleIndex = MAX_RECORD_COUNT / 2;

        for (int i = MAX_RECORD_COUNT; i > middleIndex; i--) {
            newBrother.keys.add(0, keys.get(i));
            newBrother.recordAddresses.add(0, recordAddresses.get(i));
            keys.remove(i);
            recordAddresses.remove(i);
        }
        handler.queueWrite(newBrother);

        Page parent = handler.readPage(parentAddress);
        int middleKey = getKeys().get(middleIndex);
        if (middleKey > parent.getKeys().get(parent.keys.size() - 1)) {
            parent.getKeys().add(middleKey);
            parent.getRecordAddresses().add(this.getRecordAddresses().get(middleIndex));
            parent.childPagesAddresses.add(parent.keys.size(), newBrother.getMyAddressInTreeFile());
        } else {
            for (int i = 0; i < keys.size(); i++) {
                if (middleKey < parent.getKeys().get(i)) {
                    parent.getKeys().add(i, middleKey);
                    parent.getRecordAddresses().add(i, this.getRecordAddresses().get(middleIndex));
                    parent.childPagesAddresses.add(i + 1, newBrother.getMyAddressInTreeFile());
                    break;
                }
            }
        }
        handler.queueWrite(parent);
        keys.remove(middleIndex);
        recordAddresses.remove(middleIndex);
    }

    void splitRoot() {
        Page leftChild = new Page(handler, myAddressInTreeFile);
        handler.allocateAddress(leftChild);
        Page rightChild = new Page(handler, myAddressInTreeFile);
        handler.allocateAddress(rightChild);

        int middleIndex = MAX_RECORD_COUNT / 2;

        for (int i = MAX_RECORD_COUNT; i > middleIndex; i--) {
            rightChild.keys.add(0, keys.get(i));
            rightChild.recordAddresses.add(0, recordAddresses.get(i));
            keys.remove(i);
            recordAddresses.remove(i);
        }
        for (int i = middleIndex - 1; i >= 0; i--) {
            leftChild.keys.add(0, keys.get(i));
            leftChild.recordAddresses.add(0, recordAddresses.get(i));
            keys.remove(i);
            recordAddresses.remove(i);
        }
        handler.queueWrite(leftChild);
        handler.queueWrite(rightChild);
        childPagesAddresses.set(0, leftChild.getMyAddressInTreeFile());
        childPagesAddresses.set(1, rightChild.getMyAddressInTreeFile());
    }

    boolean isRoot() {
        return parentAddress == NO_CHILD;
    }

    public boolean hasKey(int key) {
        return keys.contains(key);
    }

    public List<Integer> getKeys() {
        return keys;
    }

    public List<Integer> getRecordAddresses() {
        return recordAddresses;
    }

    public Integer getMyAddressInTreeFile() {
        return myAddressInTreeFile;
    }

    public void setMyAddressInTreeFile(Integer myAddressInTreeFile) {
        this.myAddressInTreeFile = myAddressInTreeFile;
    }

    @Override
    public String toString() {
        return "Page{" +
                "keys=" + keys +
                " ChildAddresses=" + childPagesAddresses +
                '}';
    }

    public Page findPageForKey(int searchedKey, boolean isInserted) throws IOException {
        if (keys.isEmpty()) {
            return this;
        }
        int counter = 0;
        while (counter < keys.size()) {
            int currentKey = keys.get(counter);
            if (searchedKey <= currentKey) {
                if (!isInserted && (searchedKey == currentKey)) {
                    return this;
                }
                if (childPagesAddresses.get(counter) != NO_CHILD) {
                    return handler.readPage(childPagesAddresses.get(counter)).findPageForKey(searchedKey, isInserted);
                } else {
                    return this;
                }
            }
            counter++;
        }
        if (childPagesAddresses.get(keys.size()) != NO_CHILD) {
            return handler.readPage(childPagesAddresses.get(keys.size())).findPageForKey(searchedKey, isInserted);
        }
        return this;
    }

    public Record getKeyFromCurrentPage(int searchedKey) throws IOException {
        int counter = 0;
        while (counter < keys.size()) {
            int currentKey = keys.get(counter);
            if (currentKey == searchedKey) {
                return handler.readData(recordAddresses.get(counter));
            }
            counter++;
        }
        return null;
    }

    public List<Integer> getChildPagesAddresses() {
        return childPagesAddresses;
    }

    public int getParentAddress() {
        return parentAddress;
    }

    public void deleteRecord(int key) throws IOException {
        int deletedKeyIndex = getKeys().lastIndexOf(key);
        if (this.isLeaf()) {
            getKeys().remove(deletedKeyIndex);
            getRecordAddresses().remove(deletedKeyIndex);
            manager.handleUnderflow(this);
        } else {
            int rightChildAddress = childPagesAddresses.get(deletedKeyIndex + 1);
            int leftChildAddress = childPagesAddresses.get(deletedKeyIndex);
            Page leftChild;
            Page rightChild;
            if (leftChildAddress == NO_CHILD) {
                rightChild = handler.readPage(rightChildAddress);

                replaceRemovedWithProperDescendant(rightChild, deletedKeyIndex, true);

                handler.queueWrite(this);

            } else if (rightChildAddress == NO_CHILD) {
                leftChild = handler.readPage(leftChildAddress);
                replaceRemovedWithProperDescendant(leftChild, deletedKeyIndex, false);

                handler.queueWrite(this);
            } else {
                leftChild = handler.readPage(leftChildAddress);
                rightChild = handler.readPage(rightChildAddress);

                boolean rightChildHasMoreRecords = rightChild.getKeys().size() > leftChild.getKeys().size();
                if (rightChildHasMoreRecords) {
                    replaceRemovedWithProperDescendant(rightChild, deletedKeyIndex, true);

                    handler.queueWrite(this);


                } else {
                    replaceRemovedWithProperDescendant(leftChild, deletedKeyIndex, false);

                    handler.queueWrite(this);
                }
            }
        }

    }


    void mergeWith(Page brotherPage, boolean ourBrotherIsLeft) throws IOException {
        Page parent = handler.readPage(parentAddress);
        int myIndex = parent.childPagesAddresses.lastIndexOf(myAddressInTreeFile);
        if (ourBrotherIsLeft) {
            getKeys().add(0, parent.getKeys().get(myIndex - 1));
            getRecordAddresses().add(0, parent.getRecordAddresses().get(myIndex - 1));
            parent.getKeys().remove(myIndex - 1);
            parent.getRecordAddresses().remove(myIndex - 1);

            for (int i = brotherPage.getKeys().size() - 1; i >= 0; i--) {
                getKeys().add(0, brotherPage.getKeys().get(i));
                getRecordAddresses().add(0, brotherPage.getRecordAddresses().get(i));
            }

            parent.childPagesAddresses.set(myIndex - 1, parent.childPagesAddresses.get(myIndex));
            parent.childPagesAddresses.set(myIndex, parent.childPagesAddresses.get(myIndex + 1));
            parent.childPagesAddresses.set(myIndex + 1, Page.NO_CHILD);
            handler.writePage(parent);
            handler.writePage(this);
        } else {
            getKeys().add(parent.getKeys().get(myIndex));
            getRecordAddresses().add(parent.getRecordAddresses().get(myIndex));
            parent.getKeys().remove(myIndex);
            parent.getRecordAddresses().remove(myIndex);

            for (int i = 0; i < brotherPage.getKeys().size(); i++) {
                getKeys().add(brotherPage.getKeys().get(i));
                getRecordAddresses().add(brotherPage.getRecordAddresses().get(i));
            }

            parent.childPagesAddresses.set(myIndex + 1, parent.childPagesAddresses.get(myIndex + 2));
            parent.childPagesAddresses.set(myIndex + 2, Page.NO_CHILD);
            handler.writePage(parent);
            handler.writePage(this);
        }
    }

    boolean isNotHungry() {
        return getKeys().size() > MIN_RECORD_COUNT;
    }

    private void replaceRemovedWithProperDescendant(Page childPage, int deletedKeyIndex, boolean findSmallest) throws IOException {
        Page searchedPage;
        int offset;
        if (findSmallest) {
            searchedPage = childPage.smallestInTree();
            offset = 0;
        } else {
            searchedPage = childPage.biggestInTree();
            offset = searchedPage.getKeys().size() - 1;
        }

        getKeys().set(deletedKeyIndex, searchedPage.getKeys().get(offset));
        getRecordAddresses().set(deletedKeyIndex, searchedPage.getRecordAddresses().get(offset));

        searchedPage.getKeys().remove(offset);
        searchedPage.getRecordAddresses().remove(offset);

        manager.handleUnderflow(searchedPage);
        handler.writePage(searchedPage);
    }

    private Page biggestInTree() throws IOException {
        if (childPagesAddresses.get(keys.size()) != NO_CHILD) {
            Page child = handler.readPage(childPagesAddresses.get(keys.size()));
            return child.biggestInTree();
        } else {
            return this;
        }
    }

    private Page smallestInTree() throws IOException {

        if (childPagesAddresses.get(0) != NO_CHILD) {
            Page child = handler.readPage(childPagesAddresses.get(0));
            return child.smallestInTree();
        } else {
            return this;
        }
    }

}
