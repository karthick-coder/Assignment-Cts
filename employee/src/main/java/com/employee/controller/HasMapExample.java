package com.employee.controller;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;

public class HasMapExample {

	public static void main(String[] args) {
		HashMap<String, Integer> map = new HashMap<>();
		map.put("A", 1);
		map.put("B", 2);
		map.put("C", 3);
		map.put("D", 4);
		map.put("E", 5);
		map.entrySet().forEach(System.out::println);
		map.keySet().forEach(System.out::println);
		map.values().forEach(System.out::println);
		map.forEach((k,v)-> {
			System.out.println("Key value is " + k + " value is " + v);
		});
				
		String s1 = "karthick";
		String s2 ="karthick";
		String s3 = new String("karthick");
		String s4 = new StringBuilder("karthick").toString();
		System.out.println(s1.substring(1));
		if(s1==s2){
			System.out.println("true");
		}else{
			System.out.println("false");	
		}
		String s = "civilc";
		boolean palindrome = IntStream.range(0, s.length()/2).noneMatch(index-> s.charAt(index) != s.charAt(s.length()-index-1));
	

StringBuffer finalString = new StringBuffer();
String blogName = "how to do in java dot com";
String[] tokens =  blogName.split(" ");
System.out.println(Arrays.toString(tokens));
for(String token:tokens){
	String reverse = new StringBuilder(token).reverse().toString();
	finalString.append(reverse+" ");
	
}
System.out.println(finalString);
	}

}
