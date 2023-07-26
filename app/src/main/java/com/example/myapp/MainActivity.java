package com.example.myapp;

/**
 * 功能：
 * 作者：wej
 * 日期：2023年07月15日
 */

public class MainActivity {
    static {
        System.loadLibrary("hello");
    }

    public native String getHelloString();

    public static void main(String[] args) {
        MainActivity mainActivity = new MainActivity();
        System.out.println(mainActivity.getHelloString());
    }
}