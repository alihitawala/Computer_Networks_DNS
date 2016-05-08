package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.*;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by aliHitawala on 4/25/16.
 */
public class SimpleDNSHandler {
    private String serverIp;
    private List<Amazon> amazonIpDetails;
    private DatagramSocket serverSocket;
    private List<State> states = new ArrayList<>();
    private static final int UDP_CLIENT_PORT = 8053;
    private static final int UDP_SERVER_PORT = 53;
    private static final int UDP_BUFFER_SIZE = 2048;
    private static final List<Short> VALID_TYPES = new ArrayList<>();
    static {
        VALID_TYPES.add(DNS.TYPE_A);
        VALID_TYPES.add(DNS.TYPE_AAAA);
        VALID_TYPES.add(DNS.TYPE_CNAME);
        VALID_TYPES.add(DNS.TYPE_NS);
    }

    public SimpleDNSHandler(String serverIp, List<Amazon> amazonIpDetails) throws SocketException, UnknownHostException {
        this.serverIp = serverIp;
        this.amazonIpDetails = amazonIpDetails;
        InetAddress aHost = InetAddress.getByName("localhost");
        this.serverSocket = new DatagramSocket(UDP_CLIENT_PORT, InetAddress.getByName("0.0.0.0"));
        this.serverSocket.setSoTimeout(5000);
    }

    public void start() {
        try {
            byte[] data = new byte[UDP_BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);

            while (true) {
                try {
                    this.serverSocket.setSoTimeout(5000);
                    this.serverSocket.receive(receivePacket);
                    this.serverSocket.setSoTimeout(0);
                    byte[] receivedData = receivePacket.getData();
                    this.states.clear();
                    DNS dnsPacket = DNS.deserialize(receivedData, receivedData.length);
                    DNS receivedAnswer = handleMain(dnsPacket);
                    sendResponse(receivedAnswer, receivePacket.getAddress(), receivePacket.getPort(), dnsPacket.getId());
                }
                catch (SocketTimeoutException e) {
                    //do nothing
//                    System.out.println("Socket Timeout Exception!!");
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            this.serverSocket.close();
        }
    }

    private DNS handleMain(DNS dnsPacket) throws IOException {
        if (!isPacketValid(dnsPacket))
            return null;
        boolean recursionDesired;
        List<DNSQuestion> questions;
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(5000);
        recursionDesired = dnsPacket.isRecursionDesired();
        questions = dnsPacket.getQuestions();
        byte[] data = new byte[UDP_BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(data, data.length);
        handlePacketQuery(socket, dnsPacket);
        DNS resultDNSPacket = null;
        while (true) {
            socket.setSoTimeout(5000);
            socket.receive(receivePacket);
            socket.setSoTimeout(0);
            byte[] receivedData = receivePacket.getData();
            DNS receivedDns = DNS.deserialize(receivedData, receivedData.length);
            if (!recursionDesired) {
                List<DNSResourceRecord> answers = receivedDns.getAnswers();
                populateAnswers(answers);
                receivedDns.setQuestions(questions);
                resultDNSPacket = receivedDns;
                break;
            }
            if (!receivedDns.getAnswers().isEmpty()) {
                if (isRecordAvailable(receivedDns)) {
                    checkAndChangeForAmazon(receivedDns);
                    List<DNSResourceRecord> answers = receivedDns.getAnswers();
                    populateAnswers(answers);
                    receivedDns.setQuestions(questions);
                    resultDNSPacket = receivedDns;
                    break;
                }
                else {
                    boolean found = false;
                    for (DNSResourceRecord record : receivedDns.getAnswers()) {
                        if (record.getType() == DNS.TYPE_CNAME) {
                            State state = new State(record.getName(), record.getType(), record.getData().toString(),
                                    record.getTtl(), record.getCls());
                            this.states.add(state);
                            DNS temp = new DNS();
                            temp.setOpcode(DNS.OPCODE_STANDARD_QUERY);
                            temp.setQuery(true);
                            temp.setRecursionAvailable(true);
                            temp.setRecursionDesired(true);
                            DNSQuestion question = new DNSQuestion(record.getData().toString(), DNS.TYPE_A);
                            temp.addQuestion(question);
                            DNS afterCnameDns = handleMain(temp);
                            afterCnameDns.setQuestions(questions);
                            resultDNSPacket = afterCnameDns;
                            found = true;
                            break;
                        }
                    }
                    if (found)
                        break;
                }
//                System.out.println("Gotcha!!");
            }
            else {
                handlePacketResponse(socket, receivedDns);
            }
        }
        socket.close();
        return resultDNSPacket;
    }

    private void checkAndChangeForAmazon(DNS receivedDns) {
        DNSQuestion question = receivedDns.getQuestions().get(0);
        if (question.getType() == DNS.TYPE_A) {
            String region = getAmazonRegion(receivedDns.getAnswers());
            if (region == null)
                return;
            DNSRdataString string = new DNSRdataString(region);
            DNSResourceRecord record = new DNSResourceRecord(question.getName(), DNS.TYPE_TXT, string);
            receivedDns.addAnswer(record);
        }
    }

    private String getAmazonRegion(List<DNSResourceRecord> answers) {
        for (DNSResourceRecord record : answers) {
            byte[] ip = record.getData().serialize();
            for (Amazon amazon : this.amazonIpDetails) {
                if ((SimpleDNS.toIPv4Address(amazon.getIp()) & SimpleDNS.toIPv4Address(amazon.getMask())) ==
                        (SimpleDNS.toIPv4Address(ip) & SimpleDNS.toIPv4Address(amazon.getMask()))) {
                    return amazon.getRegion() + "-" + SimpleDNS.fromIPv4Address(SimpleDNS.toIPv4Address(ip));
                }
            }
        }
        return null;
    }

    private void populateAnswers(List<DNSResourceRecord> answers) {
        for (int i=this.states.size()-1;i>=0;i--) {
            State state = this.states.get(i);
            DNSRdataName data = new DNSRdataName(state.data);
            DNSResourceRecord record = new DNSResourceRecord(state.name, state.type, data);
            record.setTtl(state.ttl);
            answers.add(0, record);
        }
    }

    private boolean isRecordAvailable(DNS dnsPacket) {
        short type = dnsPacket.getQuestions().get(0).getType();
        String name = dnsPacket.getQuestions().get(0).getName();
        for (DNSResourceRecord record : dnsPacket.getAnswers()) {
            if (record.getType() == type && name != null && record.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void sendResponse(DNS receivedPacket, InetAddress clientAddress, int clientPort, short id) {
        DNS dnsPacket = new DNS();
        dnsPacket.setOpcode(DNS.OPCODE_STANDARD_QUERY);
        dnsPacket.setQuery(false);
        dnsPacket.setRecursionAvailable(true);
        dnsPacket.setRecursionDesired(false);
        dnsPacket.setQuestions(receivedPacket.getQuestions());
        dnsPacket.setId(id);
        dnsPacket.setAnswers(receivedPacket.getAnswers());
        dnsPacket.setAdditional(receivedPacket.getAdditional());
        dnsPacket.setAuthorities(receivedPacket.getAuthorities());
        byte[] data = dnsPacket.serialize();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length,
                clientAddress, clientPort);
        try {
            this.serverSocket.send(sendPacket);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    private DatagramPacket handlePacketQuery(DatagramSocket socket, String name, short type) throws IOException {
        sendQuery(socket, name, type, SimpleDNS.toIPv4AddressBytes(this.serverIp));
        return null;
    }

    private DatagramPacket handlePacketQuery(DatagramSocket socket, DNS dnsPacket) throws IOException {
        DNSQuestion question = dnsPacket.getQuestions().get(0);
        short type = question.getType();
        String name = question.getName();
        handlePacketQuery(socket, name, type);
        return null;
    }

    private void sendQuery(DatagramSocket socket, String name, short type, byte[] ip) throws IOException {
        DNS dnsPacket = new DNS();
        dnsPacket.setOpcode(DNS.OPCODE_STANDARD_QUERY);
        dnsPacket.setQuery(true);
        dnsPacket.setRecursionAvailable(true);
        dnsPacket.setRecursionDesired(false);
        DNSQuestion question = new DNSQuestion(name, type);
        dnsPacket.addQuestion(question);
        byte[] data = dnsPacket.serialize();
        try {
            DatagramPacket sendPacket = new DatagramPacket(data, data.length,
                InetAddress.getByAddress(ip), UDP_SERVER_PORT);
            socket.send(sendPacket);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePacketResponse(DatagramSocket socket, DNS dnsPacket) throws IOException {
        List<DNSResourceRecord> additional = dnsPacket.getAdditional();
        String name = dnsPacket.getQuestions().get(0).getName();
        short type = dnsPacket.getQuestions().get(0).getType();
        byte[] nextServerIp = new byte[0];
        boolean found = false;
        for (DNSResourceRecord record : additional) {
            if (record.getType() == DNS.TYPE_A) {
                nextServerIp = record.getData().serialize();
                found = true;
                break;
            }
        }
        if (!found) {
            //get the server ip recursively from authority section
            List<DNSResourceRecord> authorities = dnsPacket.getAuthorities();
            String serverName = "";
            for (DNSResourceRecord record: authorities) {
                if (record.getType() == DNS.TYPE_NS) {
                    serverName = ((DNSRdataName)record.getData()).getName();
                    break;
                }
            }
            if (serverName.equals(""))
                return;
            DNS temp = new DNS();
            temp.setOpcode(DNS.OPCODE_STANDARD_QUERY);
            temp.setQuery(true);
            temp.setRecursionAvailable(true);
            temp.setRecursionDesired(true);
            DNSQuestion question = new DNSQuestion(serverName, DNS.TYPE_A);
            temp.addQuestion(question);
            DNS ipNS_DNS = handleMain(temp);
            for (DNSResourceRecord record : ipNS_DNS.getAnswers()) {
                if (record.getType() == DNS.TYPE_A) {
                    nextServerIp = SimpleDNS.toIPv4AddressBytes(record.getData().toString());
                    break;
                }
            }
        }
        try {
            sendQuery(socket, name, type, nextServerIp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isPacketValid(DNS dnsPacket) {
        List<DNSQuestion> questions = dnsPacket.getQuestions();
        boolean result = dnsPacket.getOpcode() == DNS.OPCODE_STANDARD_QUERY;
        if (questions == null || !result)
            return result;
        for (DNSQuestion question: questions) {
            Short type = question.getType();
            if (!VALID_TYPES.contains(type)) {
                result = false;
                break;
            }
        }
        return result;
    }

    private static class State {
        private String name;
        private short type;
        private String data;
        private int ttl;
        private short cls;

        public State(String name, short type, String data, int ttl, short cls) {
            this.name = name;
            this.type = type;
            this.data = data;
            this.ttl = ttl;
            this.cls = cls;
        }
    }
}
