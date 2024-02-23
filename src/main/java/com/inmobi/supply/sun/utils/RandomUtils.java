package com.inmobi.supply.sun.utils;

import com.inmobi.supply.component.constant.TermCode;
import com.inmobi.supply.sun.exception.SunException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class RandomUtils {

    /*
     * 随机计算0-100的值。然后与per进行比较
     * */
    public static boolean dropByPercent(int per) {
        if (per > 100 || per < 0) {
            return false;
        }
        int num = ThreadLocalRandom.current().nextInt(0, 100);
        if (num >= (100 - per)) {
            return true;
        }
        return false;
    }

    /*
     * 随机计算0-100的值。然后与per进行比较
     * */
    public static boolean dropByHour(Map<?,?> map) {
        //获取当前hour int
        try {
            int per  =(Integer) map.get(getHour());
            if (per > 100 || per < 0) {
                return false;
            }
            int num = ThreadLocalRandom.current().nextInt(0, 100);
            if (num >= (100 - per)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("dropByHour error map {}", map);
        }
        return false;
    }

    private static String getHour(){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        return String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
    }

    public static void main(String[] args) {
        int j = 0;
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;
        int e = 0;
        int f = 0;
        while (true){
            j++;
            if(j > 2000){
                break;
            }
            int i = groupByRandom(3);
            switch (i){
                case 0:
                    f++;
                    break;
                case 1:
                    a++;
                    break;
                case 2:
                    b++;
                    break;
                case 3:
                    c++;
                    break;
                case 4:
                    d++;
                    break;
                default:
                    e++;
                    break;
            }

        }

        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
        System.out.println(d);
        System.out.println(e);
        System.out.println(f);
        System.out.println(a+b+c+d+e+f);
    }


    /**
     * 随机均匀分布到不同组
     * @param groupNum
     * @return
     */
    public static int groupByRandom(int groupNum) {
        if(groupNum <= 0){
            return 1;
        }
        int num = ThreadLocalRandom.current().nextInt(0, 100);
        int size = 100 / groupNum;
        ArrayList<ArrayList<Integer>> arrayLists = new ArrayList<>();
        for (int i = 1; i <= groupNum; i++) {
            ArrayList<Integer> integersElement = new ArrayList<>();
            for (int j = 0; j < i*size; j++) {
                integersElement.add(j);
            }
            arrayLists.add(integersElement);
        }
        for (int i = 1; i <= arrayLists.size(); i++) {
            ArrayList<Integer> integers = arrayLists.get(i - 1);
            if(integers.contains(num)){
                return i;
            }
        }
        return 1;
    }
}
