package com.hdn.demo;

import java.util.Arrays;

public class Test {

    public static void main(String[] args) {
        int[] arr = {1,2,3,4,5,6};
        String s1 = Arrays.toString(arr).replaceAll("\\[|\\]", "");
        System.out.println(s1);
        String s2 = s1.replaceAll(",\\s", ",");
        System.out.println(s2);
    }
}
