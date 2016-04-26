package edu.wisc.cs.sdn.simpledns;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleDNS
{
	private static Map<String, String> argMap = new HashMap<>();
	private static String ERROR_WRONG_ARG = "Error: missing or additional arguments";

	public static void main(String[] args)
	{
		ArgumentHandler argumentHandler = new ArgumentHandler(args);
		Map<String, String> optionsMap = argumentHandler.getOptionsMap();
		String serverIp = optionsMap.get("r");
		List<Amazon> amazonIpDetails = readCSV(optionsMap.get("e"));

		
		for (Amazon amazon : amazonIpDetails) {
			System.out.println("IP Address :: " + amazon.getIp() + " Mask :: " + amazon.getMask() + " Region :: " + amazon.getRegion());
		}
		System.out.println("IP address is :: " + optionsMap.get("r"));
		System.out.println("File Path is :: " + optionsMap.get("e"));
		System.out.println("Hello, DNS!");


		new SimpleDNSHandler(serverIp, amazonIpDetails). start();
	}

	private static List<Amazon> readCSV(String csvFile) {
		List<Amazon> amazonList = new ArrayList<>();
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";
		try {
			br = new BufferedReader(new FileReader(csvFile));
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(cvsSplitBy);
				String addr = parts[0];
				String region = parts[1];
				String[] addrParts = addr.split("/");
				String ip = addrParts[0];
				int prefix;
				if (addrParts.length < 2) {
					prefix = 0;
				} else {
					prefix = Integer.parseInt(addrParts[1]);
				}
				int m = ~((1 << (32 - prefix)) - 1);
				String mask = fromIPv4Address(m);
				amazonList.add(new Amazon(ip, mask, region));

			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return amazonList;
	}

	public static String fromIPv4Address(int ipAddress) {
		StringBuilder sb = new StringBuilder();
		int result = 0;
		for (int i = 0; i < 4; ++i) {
			result = (ipAddress >> ((3-i)*8)) & 0xff;
			sb.append(Integer.valueOf(result).toString());
			if (i != 3)
				sb.append(".");
		}
		return sb.toString();
	}

	public static int toIPv4Address(String ipAddress) {
		if (ipAddress == null)
			throw new IllegalArgumentException("Specified IPv4 address must" +
					"contain 4 sets of numerical digits separated by periods");
		String[] octets = ipAddress.split("\\.");
		if (octets.length != 4)
			throw new IllegalArgumentException("Specified IPv4 address must" +
					"contain 4 sets of numerical digits separated by periods");

		int result = 0;
		try {
			for (int i = 0; i < 4; ++i) {
				result |= Integer.valueOf(octets[i]) << ((3 - i) * 8);
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Integer expected in IP Address!");
		}
		return result;
	}

	static class ArgumentHandler {
		private Map<String, String> optionsMap = new HashMap<>();
		List<String> optionList = new ArrayList<>();

		public ArgumentHandler(String[] args) {
			String option = "";
			for (String arg : args) {
				boolean isOption = arg.charAt(0) == '-';
				if (isOption) {
					option = arg.substring(1);
					optionList.add(option);
				} else {
					optionsMap.put(option, arg);
				}
			}
			try {
				checkArguments(optionList, optionsMap);
			} catch (Exception e) {
				System.out.println(e.getMessage());
				System.exit(1);
			}
		}

		public Map<String, String> getOptionsMap() {
			return optionsMap;
		}

		private void checkArguments(List<String> optionList, Map<String, String> argMap) {
			boolean condition = optionList.contains("r") && optionList.contains("e");
			if (!condition) {
				throw new RuntimeException(ERROR_WRONG_ARG);
			}
			try {
				//to check if ip address is correct
				toIPv4Address(argMap.get("r"));
				File f = new File(argMap.get("e"));
				if(!f.isFile()) {
					throw new RuntimeException("Wrong file path or wrong name.");
				}

			} catch (Exception e) {
				throw new RuntimeException(e.getMessage());
			}
		}
	}
}
