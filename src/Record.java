import java.util.List;

public class Record {
    private  List<Float> set = null;
    private int mainFileOffset;

    public Record() {}
    public Record(List<Float> set) {
        this.set = set;
    }

    public void setMainFileOffset(int mainFileOffset) {
        this.mainFileOffset = mainFileOffset;
    }

    public int getMainFileOffset() {
        return mainFileOffset;
    }

    public List<Float> getSet() {
        return set;
    }

    public void setSet(List<Float> set) {
        this.set = set;
    }

    @Override
    public String toString() {
        return "Record{" +
                "set=" + set +
                '}';
    }
}
