import java.io.IOException;

public class TreeManager {
    private final IOHandler handler;

    public TreeManager(IOHandler handler) {
        this.handler = handler;
    }

    public void handleOverflow(Page page) throws IOException {
        if (page.isRoot()) {
            page.splitRoot();
        } else {
            Page parent = handler.readPage(page.getParentAddress());

            Page leftBrother = page.getBrother(parent, true);
            Page rightBrother = page.getBrother(parent, false);

            if (leftBrother != null && leftBrother.isLeaf() && leftBrother.isHungry()) {
                page.compensate(true, leftBrother, parent);
            } else if (rightBrother != null && rightBrother.isLeaf() && rightBrother.isHungry()) {
                page.compensate(false, rightBrother, parent);
            } else {
                page.splitChild();
            }
        }
    }

    public void handleUnderflow(Page page) throws IOException {
        if(page.getKeys().size() < Page.MIN_RECORD_COUNT && !page.isRoot()) {
            Page parent = handler.readPage(page.getParentAddress());

            Page leftBrother = page.getBrother(parent, true);
            Page rightBrother = page.getBrother(parent, false);

            if (leftBrother != null && leftBrother.isLeaf() && leftBrother.isNotHungry()) {
                leftBrother.compensate(false, page, parent);
            } else if (rightBrother != null && rightBrother.isLeaf() && rightBrother.isNotHungry()) {
                rightBrother.compensate(true, page, parent);
            } else {
                if(leftBrother == null && rightBrother == null) {
                    int myAddressIdxInParent = parent.getChildPagesAddresses().lastIndexOf(page.getMyAddressInTreeFile());
                    for(int i = page.getKeys().size() - 1; i >= 0; i--) {
                        parent.getKeys().add(myAddressIdxInParent, page.getKeys().get(i));
                        parent.getRecordAddresses().add(myAddressIdxInParent, page.getRecordAddresses().get(i));

                    }
                    parent.getChildPagesAddresses().set(myAddressIdxInParent, Page.NO_CHILD);
                    handler.writePage(parent);

                    handleOverflow(parent);
                }
                else if(rightBrother == null) {
                    page.mergeWith(leftBrother, true);
                }
                else {
                    page.mergeWith(rightBrother, false);
                }
            }
        }
        else {
            handler.writePage(page);
        }
    }
}
