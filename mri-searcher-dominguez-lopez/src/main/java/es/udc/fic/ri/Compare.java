package es.udc.fic.ri;

import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import org.apache.commons.math3.stat.inference.TTest;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Compare {
    public static void main(String[] args) {
        String usage =
                "java es.udc.fic.ri.IndexTrecCovid"
                        + " [-test t | wilcoxon ALPHA] [results <RESULTS1> <RESULTS2>]";

        String testType = null;
        float alpha = 0;
        String results1 = null;
        String results2 = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-test" -> {
                    testType = args[++i];
                    alpha = Float.parseFloat(args[++i]);
                }
                case "-results" -> {
                    results1 = args[++i];
                    results2 = args[++i];
                }
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        if (testType == null | results1 == null | results2 == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        try{
            List<String> lines1 = Files.readAllLines(Paths.get(results1));
            List<String> lines2 = Files.readAllLines(Paths.get(results2));
            double[] values1 = readValues(lines1);
            double[] values2 = readValues(lines2);
            double value;
            if(testType.equals("t")){
                TTest tTest = new TTest();
                value = tTest.tTest(values1, values2);
            }else{
                WilcoxonSignedRankTest wilcoxon = new WilcoxonSignedRankTest();
                value = wilcoxon.wilcoxonSignedRankTest(values1, values2, true);
            }
            System.out.println("Result: " + value);
            if(value < alpha){
                System.out.println("Null hypothesis rejected");
            }else{
                System.out.println("Null hypothesis accepted");
            }
        }catch (Exception e){
            System.err.println("Error reading files");
            System.exit(1);
        }



    }
    static double[] readValues(List<String> lines){
        List<Double> ret = new ArrayList<>();
        for(int i = 1; i < lines.size() - 1; i++){
            ret.add(Double.parseDouble(lines.get(i).split(",")[1]));
        }
        return ret.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
