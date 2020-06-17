package com.owl.downloader.core;//public class hello {
//    public static void main(String[] args){
//        Dog jiaohuang = new Dog("jiaohuang");
//        jiaohuang.barking();
//    }
//}
//
//class Dog{
//    public Dog(String name){
//        System.out.println("名字：" + name);
//    }
//    String breed;
//    int age;
//    String color;
//    void barking(){
//        System.out.println("我是" + "jiaohuang");
//    }
//    void hungry(){
//    }
//    void sleeping(){
//    }
//}


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.*;
import java.util.Objects;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

public class ftpTest {

    /**
     * main test
     *
     * @param args not used
     */
    public static void main(String args[]) throws IOException {
        FTPClient client = new FTPClient();
        String hostName = "public.sjtu.edu.cn";
        String userName = "xzfang";
        String psw = "public";
        String path = "/Reference Books/LM3S9B96_EN.pdf";
        String workingPath = "/Reference Books";
        String fileName = "LM3S9B96_EN.pdf";
        int serverPort = 21;

        try{
            // init
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false); //non-blocking

            // connect
            InetSocketAddress address = new InetSocketAddress(hostName, serverPort);
            socketChannel.connect(address);

            boolean connected = socketChannel.finishConnect();
            while (!connected) {
                connected = socketChannel.finishConnect();
            }
            System.out.println("是否连接" + socketChannel.isConnected());

            // login
            ByteBuffer logInBuffer = ByteBuffer.wrap(("User" + userName +"\r\n").getBytes());
            ByteBuffer logInResponseBuffer = ByteBuffer.allocate(16);
            socketChannel.write(logInBuffer);
            // TODO: check if log in succeed; not find a way to get the check code yet

            logInBuffer = ByteBuffer.wrap(("PASS" + psw + "\r\n").getBytes());
            socketChannel.write(logInBuffer);
            // TODO: check if log in succeed; not find a way to get the check code yet



//            String requestMessage = ("GET " + path + " 'HTTP/1.1\r\n") +
//                    "Host:" + host + "\r\n" +
//                    "Range: bytes=" + block.offset + "-" + (block.length + block.offset - 1) + "\r\n" +
//                    "Connection: close\r\n" +
//                    "\r\n";
//            ByteBuffer requestBuffer = ByteBuffer.wrap(requestMessage.getBytes());
//            boolean connected = socketChannel.finishConnect();
//            while (!connected) {
//                connected = socketChannel.finishConnect();
//            }
//            socketChannel.write(requestBuffer);
        }catch(IOException e){
            e.printStackTrace();
        }




//        try {
//            client.connect(hostName, serverPort);
//            client.login(userName, psw);
//        }catch(IOException e){
//            e.printStackTrace();
//            System.out.println("链接登录失败");
//        }
//        System.out.println("链接登录成功");
//
//        client.enterLocalPassiveMode();
//        client.setFileType(FTP.BINARY_FILE_TYPE);
//
//        try {
//            client.changeWorkingDirectory(workingPath);
//        } catch (IOException e) {
//            System.out.println("路径切换失败");
//        }
//        System.out.println("路径切换成功");
//
//        FTPFile[] files = client.listFiles(path);

//        System.out.println("文件长度：" + files[0].getSize() + "kB");





        //        try {
////            client.sendCommand("LIST " + fileName + "\r\n");
//            client.sendCommand("SYST " + "\r\n");
//        } catch (IOException e) {
//            System.out.println("指令发送失败");
//        }
//        System.out.println("指令发送成功");
//
//        System.out.println(client.getReplyCode());


    }
}
