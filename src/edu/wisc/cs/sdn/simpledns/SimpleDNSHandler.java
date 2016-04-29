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
    private static final int UDP_CLIENT_PORT = 18053;
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
        InetAddress aHost = InetAddress.getByName("0.0.0.0");
        this.serverSocket = new DatagramSocket(UDP_CLIENT_PORT, aHost);
    }

    public void start() {
        try {
            byte[] data = new byte[UDP_BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(data, data.length);
            InetAddress clientAddress = null;
            int clientPort = -1;
            short clientTransactionId = -1;
            boolean recursionDesired = false;
            List<DNSQuestion> questions = null;
            List<DNSResourceRecord> additional = new ArrayList<>();
            List<DNSResourceRecord> authorities = new ArrayList<>();
            List<State> states = new ArrayList<>();
            while (true) {
                this.serverSocket.receive(receivePacket);
                byte[] receivedData = receivePacket.getData();
                DNS dnsPacket = DNS.deserialize(receivedData, receivedData.length);
                if (!isPacketValid(dnsPacket))
                    continue;
                if (dnsPacket.isQuery()) {
                    recursionDesired = dnsPacket.isRecursionDesired();
                    clientAddress = receivePacket.getAddress();
                    clientPort = receivePacket.getPort();
                    clientTransactionId = dnsPacket.getId();
                    questions = dnsPacket.getQuestions();
                    states.clear();
                    additional.clear();
                    authorities.clear();
                    handlePacketQuery(dnsPacket);
                }
                else {
                    if (!recursionDesired) {
                        additional.clear();
                        authorities.clear();
                        additional.addAll(dnsPacket.getAdditional());
                        authorities.addAll(dnsPacket.getAuthorities());
                        List<DNSResourceRecord> answers = dnsPacket.getAnswers();
                        populateAnswers(answers, states);
                        dnsPacket.setQuestions(questions);
                        sendResponse(dnsPacket, clientAddress, clientPort, clientTransactionId, additional, authorities);
                    }
                    else if (!dnsPacket.getAnswers().isEmpty()) {
                        if (isRecordAvailable(dnsPacket)) {
                            List<DNSResourceRecord> answers = dnsPacket.getAnswers();
                            populateAnswers(answers, states);
                            dnsPacket.setQuestions(questions);
                            sendResponse(dnsPacket, clientAddress, clientPort, clientTransactionId, additional, authorities);
                        }
                        else {
                            for (DNSResourceRecord record : dnsPacket.getAnswers()) {
                                if (record.getType() == DNS.TYPE_CNAME) {
                                    State state = new State(record.getName(), record.getType(), record.getData().toString(),
                                            record.getTtl(), record.getCls());
                                    states.add(state);
                                    handlePacketQuery(record.getData().toString(), DNS.TYPE_A);
                                    break;
                                }
                            }
                        }
                        System.out.println("Gotcha!!");
                    }
                    else {
                        additional.clear();
                        authorities.clear();
                        additional.addAll(dnsPacket.getAdditional());
                        authorities.addAll(dnsPacket.getAuthorities());
                        handlePacketResponse(dnsPacket);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            this.serverSocket.close();
        }
    }

    private void populateAnswers(List<DNSResourceRecord> answers, List<State> states) {
        for (int i=states.size()-1;i>=0;i--) {
            State state = states.get(i);
            DNSRdataName data = new DNSRdataName(state.data);
            DNSResourceRecord record = new DNSResourceRecord(state.name, state.type, data);
            record.setTtl(state.ttl);
            answers.add(0, record);
        }
    }

    private boolean isRecordAvailable(DNS dnsPacket) {
        short type = dnsPacket.getQuestions().get(0).getType();
        for (DNSResourceRecord record : dnsPacket.getAnswers()) {
            if (record.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private void sendResponse(DNS receivedPacket, InetAddress clientAddress, int clientPort, short id,
                              List<DNSResourceRecord> additional, List<DNSResourceRecord> authorities) {
        DNS dnsPacket = new DNS();
        dnsPacket.setOpcode(DNS.OPCODE_STANDARD_QUERY);
        dnsPacket.setQuery(false);
        dnsPacket.setRecursionAvailable(true);
        dnsPacket.setRecursionDesired(false);
        dnsPacket.setQuestions(receivedPacket.getQuestions());
        dnsPacket.setId(id);
        dnsPacket.setAnswers(receivedPacket.getAnswers());
        dnsPacket.setAdditional(additional);
        dnsPacket.setAuthorities(authorities);
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


    private DatagramPacket handlePacketQuery(String name, short type) throws IOException {
        sendQuery(name, type, SimpleDNS.toIPv4AddressBytes(this.serverIp));
        return null;
    }

    private DatagramPacket handlePacketQuery(DNS dnsPacket) throws IOException {
        List<DNSQuestion> questions = dnsPacket.getQuestions();
        for (DNSQuestion question: questions) {
            short type = question.getType();
            String name = question.getName();
            handlePacketQuery(name, type);
        }
        return null;
    }

    private void sendQuery(String name, short type, byte[] ip) throws IOException {
        DNS dnsPacket = new DNS();
        dnsPacket.setOpcode(DNS.OPCODE_STANDARD_QUERY);
        dnsPacket.setQuery(true);
        dnsPacket.setRecursionAvailable(true);
        dnsPacket.setRecursionDesired(false);
        DNSQuestion question = new DNSQuestion(name, type);
        dnsPacket.addQuestion(question);
        byte[] data = dnsPacket.serialize();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length,
                InetAddress.getByAddress(ip), UDP_SERVER_PORT);
        try {
            this.serverSocket.send(sendPacket);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePacketResponse(DNS dnsPacket) {
        List<DNSResourceRecord> additional = dnsPacket.getAdditional();
        String name = dnsPacket.getQuestions().get(0).getName();
        short type = dnsPacket.getQuestions().get(0).getType();
        byte[] nextServerIp = new byte[0];

        for (DNSResourceRecord record : additional) {
            if (record.getType() == DNS.TYPE_A) {
                nextServerIp = record.getData().serialize();
                break;
            }
        }
        try {
            sendQuery(name, type, nextServerIp);
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
