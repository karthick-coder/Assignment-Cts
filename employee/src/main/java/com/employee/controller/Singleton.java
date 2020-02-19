package com.employee.controller;

public class Singleton {
	
	private static Singleton obj;
	
	static {
		obj = new Singleton();
	}
	
	private Singleton(){
		
	}
	
	public static Singleton getInstance(){
		return obj;
		
	}
	
	public void test(){
		System.out.println("test");
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Singleton ms = getInstance();
		ms.test();
		int anar[]=new int[]{1,2,3};
		System.out.println(anar[1]); 
	}

}
