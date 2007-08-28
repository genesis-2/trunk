package edu.virginia.vcgr.genii.client.jni.gIIlib.cache;

import java.util.ArrayList;

/* Cache Entry for a File of the JNI Library */
public class JNICacheEntry {
	private String path=null;
	private String name=null;
	private boolean isDirectory=false;
	private boolean exists = true;
	private long fileSize=0;
	private long timeOfEntry;
	
	/* This field is only valid if isDirectory == true */
	private ArrayList<JNICacheEntry> directoryEntries = new ArrayList<JNICacheEntry>();
	
	public JNICacheEntry(String path, boolean isDirectory, long fileSize, String name, 
			ArrayList <JNICacheEntry> entries){
		this.path = path;
		this.isDirectory = isDirectory;
		this.fileSize = fileSize;
		this.directoryEntries = entries;
		this.timeOfEntry = System.currentTimeMillis();
		this.name = name;
	}
	
	public static JNICacheEntry createNonExistingEntry(String path){
		JNICacheEntry dummy = new JNICacheEntry(path, false, 0, null, null);
		dummy.exists = false;	
		return dummy;
	}
	
	public ArrayList<String> getFileInformation(){
		ArrayList<String> toReturn =  new ArrayList<String>();
		toReturn.add(isDirectory ? "D" : "F");
		toReturn.add(String.valueOf(fileSize));
		toReturn.add(name);
		return toReturn;
	}
	
	public long getTimeOfEntry(){
		return timeOfEntry;
	}
	
	public ArrayList<JNICacheEntry> getDirectoryEntries(){
		return directoryEntries;		
	}
	
	public void addDirectoryEntries(ArrayList<JNICacheEntry> entries){
		this.directoryEntries = entries;		
	}
	
	public String toString(){
		long age = System.currentTimeMillis() - timeOfEntry;
		if(exists){
			return (isDirectory ? "Directory: " : "File: ") + path + 
				" of size " + fileSize + " with millisecond age of " + age;
		}
		else{
			return (path + " not valid in Genesis!");
		}
			
	}
	
	public boolean exists(){
		return exists;
	}
}
