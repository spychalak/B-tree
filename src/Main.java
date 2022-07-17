import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    static IOHandler handler;
    public static void main(String[] args) throws IOException {
        boolean recordsFromFile = false;
        Scanner scanner = new Scanner(System.in);
        RandOperations random = new RandOperations();

        handler  = new IOHandler("tree.bin", "data.bin");

        if(args.length > 0) {
            recordsFromFile = true;
            scanner = new Scanner(new FileInputStream(args[0]));
        }

        boolean ifExit = false;

        while(!ifExit) {
            if(!recordsFromFile) {
                System.out.println();
                System.out.println("Wybierz opcje: ");
                System.out.println("1. Wstaw rekord");
                System.out.println("2. Usun rekord");
                System.out.println("3. Zaktualizuj rekord");
                System.out.println("4. Wyswietl drzewo");
                System.out.println("5. Odczytaj rekord");
                System.out.println("9. Wyjdz");
            }
            Page root = handler.readPage(0);
            int choice = scanner.nextInt();
            switch(choice) {
                case 1:
                    addRecord(scanner, root);
                    break;
                case 2:
                    deleteRecord(scanner, root);
                    break;
                case 3:
                    updateRecord(scanner, root);
                    break;
                case 4:
                    showTree(root);
                    break;
                case 5:
                    findRecord(scanner, root);
                    break;
                case 9:
                    ifExit = true;
                    System.out.println("Liczba operacji : " + (handler.getAllDiskOperations()));
                    System.out.println("Wyjscie z programu");
                    break;
            }
        }
    }

    private static void updateRecord(Scanner scanner, Page root) throws IOException {
        int key = scanner.nextInt(); // podanie klucza pod którym aktualizujemy rekord

        Page page = findPageForKey(key, root, false);

        if(page == null || !page.hasKey(key)) {
            System.out.println("Nie ma rekordu z podanym kluczem!");
        }
        else { // podanie wartosci nowo zaktualizowanego rekordu
            int count = scanner.nextInt();
            List<Float> set = new ArrayList<>(count);
            for(int i = 0; i < count; i++) {
                set.add(scanner.nextFloat());
            }
            Record newRecord = new Record(set);
            handler.writeData(newRecord);

            for(int i = 0; i < page.getKeys().size(); i++) {
                if(page.getKeys().get(i) == key) {
                    page.getRecordAddresses().set(i, newRecord.getMainFileOffset());
                    break;
                }
            }
            handler.queueWrite(page);
            System.out.println("Zaktualizowano rekord z kluczem rownym " + key);
        }
        handler.writeQueued();
    }

    private static void deleteRecord(Scanner scanner, Page root) throws IOException {
        int key = scanner.nextInt(); // podajemy wartość klucza do usunięcia
        Page page = findPageForKey(key, root, false);

        if(page == null || !page.hasKey(key)) {
            System.out.println("Nie ma rekordu z podanym kluczem!");
        }
        else {
            page.deleteRecord(key);
            System.out.println("Usunieto rekord z kluczem rownym " + key);
        }

        handler.writeQueued();
    }

    private static void findRecord(Scanner scanner, Page root) throws IOException {

        int searchedKey = scanner.nextInt(); // podajemy wartość klucza szukanego rekordu
        Page searchedPage = findPageForKey(searchedKey, root, true);
        if(searchedPage == null) {
            System.out.println("Nie znaleziono rekordu o podanym kluczu");
            return;
        }
        Record foundRecord = null;
        if(searchedPage.hasKey(searchedKey)) {
            foundRecord = searchedPage.getKeyFromCurrentPage(searchedKey);
        }

        if(foundRecord == null) {
            System.out.println("Nie znaleziono rekordu o podanym kluczu");
        }
        else {
            System.out.println("Znaleziono rekord dla klucza " + searchedKey + " : " + foundRecord);
        }
    }

    private static Page findPageForKey(int searchedKey, Page root, boolean isInserted) throws IOException {
        if(root == null) {
            return null;
        }
        Page result = root.findPageForKey(searchedKey, isInserted);

        return result;
    }

    private static void showTree(Page root) throws IOException {
        System.out.println("Wizualizacja B-drzewa");
        System.out.println();
        showPage(root.getMyAddressInTreeFile());

    }

    private static void showPage(Integer myAddressInTreeFile) throws IOException {
        Page page = handler.readPage(myAddressInTreeFile);
        System.out.println(page);
        for(int i = 0; i < page.getKeys().size(); i++) {
            Integer childAddress = page.getChildPagesAddresses().get(i);
            if(childAddress != Page.NO_CHILD) {
                showPage(childAddress);
            }
            System.out.println("[ Key: " + page.getKeys().get(i) + " pageAddr: " + myAddressInTreeFile + " prntAddr: " + page.getParentAddress() + " ]   " +
                    "Record: " + handler.readData(page.getRecordAddresses().get(i)));
        }
        Integer childAddress = page.getChildPagesAddresses().get(page.getKeys().size());
        if(childAddress != Page.NO_CHILD) {
            showPage(childAddress);
        }
    }

    private static void addRecord(Scanner scanner, Page root) throws IOException {
        int key = scanner.nextInt(); // podanie wartości nowego rekordu
        int count = scanner.nextInt();
        List<Float> set = new ArrayList<>(count);
        for(int i = 0; i < count; i++) {
            set.add(scanner.nextFloat());
        }

        if (root == null) {
            root = new Page(handler, Page.NO_CHILD);
            handler.allocateAddress(root);
        }

        Page page = findPageForKey(key, root, true);

        if(!page.hasKey(key)) {
            Record added = new Record(set); // Record musi znac swoje polozenie w pliku danych
            handler.writeData(added); // Tu wpisujemy jego polozenie (offset)
            page.addRecord(added, key);
            System.out.println("Wstawiono rekord z kluczem rownym " + key);
        }
        else {
            System.out.println("Podany rekord juz istnieje!");
        }

        handler.writeQueued();
    }
}
