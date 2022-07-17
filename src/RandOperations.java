import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandOperations {

    private static final int NR_OF_OPERATIONS = 40;
    private List<Integer> keys = new ArrayList<>();

    public RandOperations() throws IOException {
        randOperation();
    }

    private void randOperation() throws IOException {

        FileWriter operationsStream = new FileWriter("random.txt");
        Random random = new Random();
        int counter = 0;

        for (int i = 0; i < NR_OF_OPERATIONS - 5 ; i++) {

            operationsStream.write(1 + "\n");
            Integer key = random.nextInt(1000) + 1;
            keys.add(key);
            operationsStream.write(key + "\n");

            Integer count = random.nextInt(15) + 1;
            operationsStream.write(count.toString() + "\n");

            for (int j = 0; j < count; j++) {
                Float item = random.nextFloat() * 100;
                operationsStream.write(item.toString().replaceAll("\\.", ",") + "\n");
            }
            operationsStream.flush();
            counter++;
        }
        operationsStream.write(4 + "\n");

        while (counter < NR_OF_OPERATIONS) {
            int number = random.nextInt(2) + 2;
            operationsStream.write(number + "\n");

            if (number == 2) {
                int existingKey = random.nextInt(keys.size()-1) + 1;
                Integer key = keys.get(existingKey);
                operationsStream.write(key + "\n");
                keys.remove(existingKey);
            }
            if (number == 3){
                int existingKey = random.nextInt(keys.size() - 1) + 1;
                Integer key = keys.get(existingKey);
                operationsStream.write(key + "\n");
                Integer count = random.nextInt(15) + 1;
                operationsStream.write(count.toString() + "\n");
                for (int j = 0; j < count; j++) {
                    Float item = random.nextFloat() * 100;
                    operationsStream.write(item.toString().replaceAll("\\.", ",") + "\n");
                }
            }
            operationsStream.flush();
            counter++;
        }
        operationsStream.write(4 + "\n");
        operationsStream.write(9 + "\n");
        operationsStream.flush();
    }
}
